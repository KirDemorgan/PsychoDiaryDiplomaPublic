import { ActivityIndicator } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Screen } from "@/components/screen";
import { SegmentedPicker } from "@/components/segmented-picker";
import { THEME_OPTIONS, type ThemePreference } from "@/features/appearance/theme-preference";
import { useSettings, useUpdateSettings } from "@/hooks/use-settings";
import { useTheme } from "@/theme/use-theme";

export function AppearanceScreen() {
  const theme = useTheme();
  const settings = useSettings();
  const update = useUpdateSettings();

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
  const current = (value.theme ?? "SYSTEM") as ThemePreference;

  return (
    <Screen>
      <SegmentedPicker
        accessibilityLabel="Тема оформления"
        options={THEME_OPTIONS}
        value={current}
        onChange={(preference) => update.mutate({ version: value.version, body: { theme: preference } })}
      />
      <AppText variant="caption" secondary>
        Системная тема следует настройкам iOS. Размер текста и другие параметры доступности берутся из iOS.
      </AppText>
      {update.isError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {describeError(update.error)}
        </AppText>
      ) : null}
    </Screen>
  );
}
