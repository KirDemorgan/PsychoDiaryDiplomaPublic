import { Link } from "expo-router";
import { ActivityIndicator, StyleSheet, View } from "react-native";

import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { useDictation } from "@/features/voice/use-dictation";
import { MAX_RECORDING_SECONDS } from "@/features/voice/recording-options";
import { useSpeechConsent } from "@/hooks/use-speech-consent";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function DictationPanel({ onTranscript }: { onTranscript: (text: string) => void }) {
  const theme = useTheme();
  const consent = useSpeechConsent();
  const dictation = useDictation(onTranscript);

  if (consent.isPending) {
    return null;
  }
  if (!consent.data?.enabled) {
    return (
      <View style={styles.container}>
        <AppText variant="caption" secondary>
          Голосовой ввод выключен.
        </AppText>
        <Link href="/settings/ai" asChild>
          <Button title="Включить в настройках" variant="secondary" />
        </Link>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {dictation.phase === "recording" ? (
        <>
          <AppText accessibilityLiveRegion="polite" variant="callout">
            Идёт запись {formatDuration(dictation.seconds)} из {formatDuration(MAX_RECORDING_SECONDS)}
          </AppText>
          <Button title="Остановить и распознать" onPress={() => void dictation.stop()} />
        </>
      ) : dictation.phase === "processing" ? (
        <>
          <ActivityIndicator accessibilityLabel="Распознаю речь" />
          <AppText variant="callout" secondary>
            Распознаю речь…
          </AppText>
          <Button title="Отменить" variant="secondary" onPress={dictation.cancel} />
        </>
      ) : (
        <Button title="Надиктовать" variant="secondary" onPress={() => void dictation.start()} />
      )}
      {dictation.error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {dictation.error}
        </AppText>
      ) : null}
      <AppText variant="caption" secondary>
        Расшифровку можно править. Запись сохраняется только после вашего подтверждения.
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.sm },
});
