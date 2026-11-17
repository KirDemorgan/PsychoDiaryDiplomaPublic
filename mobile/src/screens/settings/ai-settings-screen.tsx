import { ActivityIndicator, StyleSheet, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import { AI_CONSENT_VERSION } from "@/api/settings-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { useSettings, useUpdateSettings } from "@/hooks/use-settings";
import { useSpeechConsent, useUpdateSpeechConsent } from "@/hooks/use-speech-consent";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function AiSettingsScreen() {
  const theme = useTheme();
  const settings = useSettings();
  const update = useUpdateSettings();
  const speech = useSpeechConsent();
  const updateSpeech = useUpdateSpeechConsent();

  if (settings.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка настроек" />
      </Screen>
    );
  }
  if (settings.isError) {
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {describeError(settings.error)}
        </AppText>
        <Button title="Повторить" variant="secondary" onPress={() => settings.refetch()} />
      </Screen>
    );
  }

  const value = settings.data;
  const toggle = (enabled: boolean) =>
    update.mutate({
      version: value.version,
      body: enabled ? { aiConsentVersion: AI_CONSENT_VERSION, aiEnabled: true } : { aiEnabled: false },
    });

  return (
    <Screen>
      <Card>
        <View style={styles.row}>
          <AppText variant="headline" style={styles.title}>
            AI-анализ записей
          </AppText>
          <Switch
            accessibilityLabel="AI-анализ записей"
            value={value.aiEnabled}
            onValueChange={toggle}
            disabled={update.isPending}
            trackColor={{ true: theme.primary }}
          />
        </View>
        <AppText secondary>
          При включении текст записей передаётся на сервер приложения и обрабатывается языковой моделью, которая
          предлагает теги с цитатами. Каждый тег вы подтверждаете сами. AI можно отключить в любой момент, записи
          при этом сохраняются.
        </AppText>
      </Card>

      {update.isError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {describeError(update.error)}
        </AppText>
      ) : null}

      <Card>
        <View style={styles.row}>
          <AppText variant="headline" style={styles.title}>
            Голосовой ввод
          </AppText>
          <Switch
            accessibilityLabel="Голосовой ввод"
            value={speech.data?.enabled === true}
            disabled={!value.aiEnabled || !speech.data || updateSpeech.isPending}
            onValueChange={(enabled) => {
              if (speech.data) {
                updateSpeech.mutate({ version: speech.data.version, enabled });
              }
            }}
            trackColor={{ true: theme.primary }}
          />
        </View>
        <AppText secondary>
          Запись голоса отправляется на сервер для распознавания и стирается после обработки, не позднее чем через 15
          минут. Расшифровка хранится до 24 часов и не становится записью дневника, пока вы её не сохраните. Требуется
          включённый AI-анализ.
        </AppText>
        {!value.aiEnabled ? (
          <AppText variant="caption" secondary>
            Сначала включите AI-анализ записей.
          </AppText>
        ) : null}
      </Card>

      {updateSpeech.isError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {describeError(updateSpeech.error)}
        </AppText>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  title: { flex: 1 },
});
