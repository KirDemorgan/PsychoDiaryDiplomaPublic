import { useEffect, useState } from "react";
import { ActivityIndicator, Linking, StyleSheet, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { readPermission, requestPermission, type ReminderPermission } from "@/features/reminders/scheduler";
import { useSettings, useUpdateSettings } from "@/hooks/use-settings";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function RemindersScreen() {
  const theme = useTheme();
  const settings = useSettings();
  const update = useUpdateSettings();
  const [permission, setPermission] = useState<ReminderPermission | null>(null);

  useEffect(() => {
    void readPermission().then(setPermission);
  }, []);

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

  const toggleReminders = async (enabled: boolean) => {
    if (enabled) {
      const granted = (await requestPermission()) === "granted";
      setPermission(granted ? "granted" : "denied");
      if (!granted) {
        return;
      }
    }
    update.mutate({ version: value.version, body: { remindersEnabled: enabled } });
  };

  return (
    <Screen>
      <Card>
        <View style={styles.row}>
          <AppText variant="headline" style={styles.title}>
            Напоминания о приёмах
          </AppText>
          <Switch
            accessibilityLabel="Напоминания о приёмах"
            value={value.remindersEnabled}
            onValueChange={(enabled) => {
              void toggleReminders(enabled);
            }}
            disabled={update.isPending}
            trackColor={{ true: theme.primary }}
          />
        </View>
        <AppText secondary>
          Уведомления создаются на этом устройстве по вашим схемам приёма и обновляются при их изменении.
        </AppText>
        {permission === "denied" ? (
          <>
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              Уведомления запрещены в настройках iOS.
            </AppText>
            <Button title="Открыть настройки iOS" variant="secondary" onPress={() => void Linking.openSettings()} />
          </>
        ) : null}
      </Card>

      <Card>
        <View style={styles.row}>
          <AppText variant="headline" style={styles.title}>
            Нейтральный текст
          </AppText>
          <Switch
            accessibilityLabel="Нейтральный текст"
            value={value.neutralNotificationText}
            onValueChange={(neutral) =>
              update.mutate({ version: value.version, body: { neutralNotificationText: neutral } })
            }
            disabled={update.isPending}
            trackColor={{ true: theme.primary }}
          />
        </View>
        <AppText secondary>
          На заблокированном экране не показываются название препарата и доза.
        </AppText>
      </Card>

      {update.isError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {describeError(update.error)}
        </AppText>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  title: { flex: 1 },
});
