import { useRouter } from "expo-router";
import { useState } from "react";
import { StyleSheet, TextInput, View } from "react-native";

import type { EntryResponse } from "@/api/entries-types";
import { describeError, isApiError } from "@/api/errors";
import type { ManualTag, TagCode, TagPresence, TagTemporal } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Chip } from "@/components/chip";
import { SegmentedPicker } from "@/components/segmented-picker";
import { PRESENCE_LABELS, PRESENCE_OPTIONS, TEMPORAL_LABELS, TEMPORAL_OPTIONS } from "@/features/tags/labels";
import { normalizeSelection, quoteOf, type TextSelection } from "@/features/tags/selection";
import { TAG_CODES, TAG_LABELS, tagColor } from "@/features/tags/tag-colors";
import { useSubmitTagReview } from "@/hooks/use-analysis";
import { localDateIn } from "@/lib/dates";
import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const STALE_MESSAGE = "Запись изменилась. Данные обновлены, начните разметку заново.";

export function ManualTagging({ entry }: { entry: EntryResponse }) {
  const theme = useTheme();
  const router = useRouter();
  const submit = useSubmitTagReview(entry.id);
  const [selection, setSelection] = useState<TextSelection>({ start: 0, end: 0 });
  const [code, setCode] = useState<TagCode>("ANXIETY");
  const [presence, setPresence] = useState<TagPresence>("PRESENT");
  const [temporal, setTemporal] = useState<TagTemporal>("CURRENT");
  const [tags, setTags] = useState<ManualTag[]>([]);
  const [error, setError] = useState<string | null>(null);
  const localDate = localDateIn(entry.timeZone, new Date(entry.occurredAt));
  const chosen = normalizeSelection(entry.text, selection);

  const addTag = () => {
    if (!chosen) {
      return;
    }
    setTags((current) => [
      ...current,
      {
        labelCode: code,
        quote: quoteOf(entry.text, chosen),
        start: chosen.start,
        end: chosen.end,
        presence,
        temporal,
        subject: "SELF",
        appliesToLocalDate: localDate,
      },
    ]);
    setSelection({ start: 0, end: 0 });
  };

  const confirm = async () => {
    setError(null);
    try {
      await submit.mutateAsync({
        version: entry.version,
        body: { revision: entry.revision, complete: true, decisions: [], manualTags: tags },
      });
      router.back();
    } catch (failure) {
      setError(isApiError(failure) && failure.code === "STALE_REVISION" ? STALE_MESSAGE : describeError(failure));
    }
  };

  return (
    <View style={styles.container}>
      <Card>
        <AppText variant="headline">Ручная разметка</AppText>
        <AppText variant="caption" secondary>
          Выделите в тексте фрагмент, выберите тег и нажмите «Добавить тег».
        </AppText>
        <TextInput
          accessibilityLabel="Текст записи для выделения"
          multiline
          value={entry.text}
          onChangeText={() => undefined}
          onSelectionChange={({ nativeEvent }) => setSelection(nativeEvent.selection)}
          showSoftInputOnFocus={false}
          style={[styles.text, { color: theme.textPrimary, backgroundColor: theme.surfaceMuted }]}
        />
        <AppText variant="callout" secondary>
          {chosen ? `Выбрано: «${quoteOf(entry.text, chosen)}»` : "Фрагмент не выбран."}
        </AppText>
      </Card>

      <View style={styles.chips} accessibilityRole="radiogroup" accessibilityLabel="Тег">
        {TAG_CODES.map((value) => (
          <Chip
            key={value}
            label={TAG_LABELS[value]}
            color={tagColor(value, theme)}
            selected={code === value}
            onPress={() => setCode(value)}
          />
        ))}
      </View>
      <SegmentedPicker accessibilityLabel="Наличие" options={PRESENCE_OPTIONS} value={presence} onChange={setPresence} />
      <SegmentedPicker accessibilityLabel="Время" options={TEMPORAL_OPTIONS} value={temporal} onChange={setTemporal} />
      <Button title="Добавить тег" variant="secondary" disabled={!chosen} onPress={addTag} />

      {tags.map((tag, index) => (
        <Card key={`${tag.start}-${tag.end}-${tag.labelCode}-${index}`}>
          <AppText variant="headline">{TAG_LABELS[tag.labelCode]}</AppText>
          <AppText variant="caption" secondary>
            «{tag.quote}» · {PRESENCE_LABELS[tag.presence]} · {TEMPORAL_LABELS[tag.temporal]}
          </AppText>
          <Button
            title="Убрать"
            variant="destructive"
            onPress={() => setTags((current) => current.filter((_, position) => position !== index))}
          />
        </Card>
      ))}

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Button
        title={tags.length === 0 ? "Завершить без тегов" : "Подтвердить теги"}
        onPress={() => {
          void confirm();
        }}
        loading={submit.isPending}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.lg },
  text: { minHeight: 120, borderRadius: radius.field, padding: spacing.md, fontSize: 17, lineHeight: 24 },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
});
