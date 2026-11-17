import { useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Alert, StyleSheet, Switch, View } from "react-native";

import { ApiError, describeError, isApiError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { ENTRY_MAX_LENGTH, isEntryTextValid } from "@/features/entries/text-limits";
import { useEntry } from "@/hooks/use-entries";
import { useDeleteEntry, useUpdateEntry } from "@/hooks/use-entry-mutations";
import { PRESENCE_LABELS } from "@/features/tags/labels";
import { TAG_LABELS } from "@/features/tags/tag-colors";
import { formatEntryMoment } from "@/lib/format";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const CONFLICT_MESSAGE = "Запись изменена на другом устройстве. Данные обновлены, повторите правку.";

export function EntryDetailScreen({ id }: { id: string }) {
  const theme = useTheme();
  const router = useRouter();
  const entry = useEntry(id);
  const update = useUpdateEntry(id);
  const remove = useDeleteEntry(id);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);

  if (entry.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка записи" />
      </Screen>
    );
  }

  if (entry.isError) {
    const notFound = isApiError(entry.error) && entry.error.status === 404;
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {notFound ? "Запись не найдена." : describeError(entry.error)}
        </AppText>
        {notFound ? null : <Button title="Повторить" variant="secondary" onPress={() => entry.refetch()} />}
      </Screen>
    );
  }

  const value = entry.data;

  const handleError = (failure: unknown) =>
    setError(failure instanceof ApiError && failure.status === 412 ? CONFLICT_MESSAGE : describeError(failure));

  const startEditing = () => {
    setDraft(value.text);
    setError(null);
    setEditing(true);
  };

  const saveEdit = async () => {
    setError(null);
    try {
      await update.mutateAsync({ version: value.version, body: { text: draft } });
      setEditing(false);
    } catch (failure) {
      handleError(failure);
    }
  };

  const confirmDelete = () =>
    Alert.alert("Удалить запись?", "Запись, её теги и результаты анализа будут удалены без возможности восстановления.", [
      { text: "Отмена", style: "cancel" },
      {
        text: "Удалить",
        style: "destructive",
        onPress: () => {
          remove.mutateAsync(value.version).then(
            () => router.back(),
            handleError,
          );
        },
      },
    ]);

  return (
    <Screen>
      <AppText variant="caption" secondary>
        {formatEntryMoment(value.occurredAt, value.timeZone)}
      </AppText>

      {editing ? (
        <>
          <TextField
            label="Текст записи"
            value={draft}
            onChangeText={setDraft}
            multiline
            autoFocus
            textAlignVertical="top"
            style={styles.input}
          />
          <View style={styles.actions}>
            <Button title="Отмена" variant="secondary" onPress={() => setEditing(false)} disabled={update.isPending} />
            <Button
              title="Сохранить"
              onPress={() => {
                void saveEdit();
              }}
              disabled={!isEntryTextValid(draft) || draft === value.text}
              loading={update.isPending}
            />
          </View>
          <AppText variant="caption" secondary>
            До {ENTRY_MAX_LENGTH} символов. После правки старый анализ тегов станет неактуальным.
          </AppText>
        </>
      ) : (
        <>
          <Card>
            <AppText>{value.text}</AppText>
          </Card>
          {value.tags.length > 0 ? (
            <Card>
              <AppText variant="headline">Теги</AppText>
              {value.tags.map((tag) => (
                <AppText key={tag.id} secondary>
                  {TAG_LABELS[tag.labelCode]} · {PRESENCE_LABELS[tag.presence]}
                </AppText>
              ))}
            </Card>
          ) : null}
          <View style={styles.visibility}>
            <AppText style={styles.visibilityLabel}>Показывать в отчёте для врача</AppText>
            <Switch
              accessibilityLabel="Показывать в отчёте для врача"
              value={value.reportVisible}
              disabled={update.isPending}
              onValueChange={(visible) => {
                setError(null);
                update.mutateAsync({ version: value.version, body: { reportVisible: visible } }).catch(handleError);
              }}
              trackColor={{ true: theme.primary }}
            />
          </View>
          <Button
            title={value.reviewComplete ? "Теги подтверждены" : "Проверить теги (AI)"}
            onPress={() => router.push({ pathname: "/entry/review/[id]", params: { id: value.id } })}
          />
          <Button title="Изменить" variant="secondary" onPress={startEditing} />
          <Button title="Удалить" variant="destructive" onPress={confirmDelete} loading={remove.isPending} />
        </>
      )}

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  input: { minHeight: 160, paddingTop: spacing.md },
  actions: { gap: spacing.md },
  visibility: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  visibilityLabel: { flex: 1 },
});
