import { useRouter } from "expo-router";
import { useCallback, useState } from "react";
import { StyleSheet, View } from "react-native";

import { analysisApi } from "@/api/analysis-api";
import { describeError } from "@/api/errors";
import { useSessionStore } from "@/auth/session-store";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { ENTRY_MAX_LENGTH, codePointLength, isEntryTextValid } from "@/features/entries/text-limits";
import { useCreateEntry } from "@/hooks/use-entry-mutations";
import { useEntryDraft, type DraftStatus } from "@/hooks/use-entry-draft";
import { useSettings } from "@/hooks/use-settings";
import { offsetDateTimeIn } from "@/lib/offset-date-time";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { DictationPanel } from "./dictation-panel";

const STATUS_TEXT: Record<DraftStatus, string> = {
  loading: "",
  empty: "",
  savedLocally: "Черновик сохранён на устройстве",
  saveFailed: "Не удалось сохранить черновик на устройстве",
};

export function NewEntryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const userId = useSessionStore((state) => state.user?.id ?? "");
  const draft = useEntryDraft(userId);
  const createEntry = useCreateEntry();
  const aiEnabled = useSettings().data?.aiEnabled === true;
  const [error, setError] = useState<string | null>(null);

  const { text, setText } = draft;
  const appendTranscript = useCallback(
    (transcript: string) => setText(text.trim() === "" ? transcript : `${text.trimEnd()} ${transcript}`),
    [text, setText],
  );

  const length = codePointLength(draft.text);
  const canSave = isEntryTextValid(draft.text) && draft.status !== "loading" && !createEntry.isPending;

  const save = async () => {
    setError(null);
    const timeZone = deviceTimeZone();
    try {
      const entry = await createEntry.mutateAsync({
        body: { text: draft.text, occurredAt: offsetDateTimeIn(timeZone), timeZone },
        idempotencyKey: draft.getIdempotencyKey(),
      });
      await draft.discard();
      if (aiEnabled) {
        await analysisApi.start(entry.id, entry.revision).catch(() => undefined);
        router.replace({ pathname: "/entry/review/[id]", params: { id: entry.id } });
      } else {
        router.back();
      }
    } catch (failure) {
      setError(`${describeError(failure)} Текст остался в черновике на устройстве.`);
    }
  };

  return (
    <Screen>
      <TextField
        label="Что произошло сегодня?"
        value={draft.text}
        onChangeText={draft.setText}
        multiline
        autoFocus
        textAlignVertical="top"
        style={styles.input}
        editable={draft.status !== "loading"}
      />
      <DictationPanel onTranscript={appendTranscript} />
      <View style={styles.meta}>
        <AppText variant="caption" secondary>
          {STATUS_TEXT[draft.status]}
        </AppText>
        <AppText variant="caption" style={length > ENTRY_MAX_LENGTH ? { color: theme.danger } : undefined} secondary={length <= ENTRY_MAX_LENGTH}>
          {length}/{ENTRY_MAX_LENGTH}
        </AppText>
      </View>

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Button
        title="Сохранить запись"
        onPress={() => {
          void save();
        }}
        disabled={!canSave}
        loading={createEntry.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  input: { minHeight: 180, paddingTop: spacing.md },
  meta: { flexDirection: "row", justifyContent: "space-between" },
});
