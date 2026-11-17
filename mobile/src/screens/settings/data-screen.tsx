import { useState } from "react";
import { Alert, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import { accountApi } from "@/api/account-api";
import { exportsApi } from "@/api/exports-api";
import { sessionService } from "@/auth/session-service";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { shareFile, ZIP_FILE } from "@/lib/share-file";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function DataScreen() {
  const theme = useTheme();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const exportData = async () => {
    setExportError(null);
    setExporting(true);
    try {
      const job = await exportsApi.create();
      const bytes = await exportsApi.file(job.id);
      await shareFile(bytes, "diary-export.zip", ZIP_FILE);
    } catch (failure) {
      setExportError(describeError(failure));
    } finally {
      setExporting(false);
    }
  };

  const deleteAccount = async () => {
    setDeleteError(null);
    setDeleting(true);
    try {
      await accountApi.deleteAccount(password);
      await sessionService.signOut();
    } catch (failure) {
      setDeleteError(describeError(failure));
      setDeleting(false);
    }
  };

  const confirmDelete = () =>
    Alert.alert("Удалить аккаунт?", "Все записи, оценки, препараты и отчёты будут удалены без возможности восстановления.", [
      { text: "Отмена", style: "cancel" },
      {
        text: "Удалить навсегда",
        style: "destructive",
        onPress: () => {
          void deleteAccount();
        },
      },
    ]);

  return (
    <Screen>
      <Card>
        <AppText variant="headline">Экспорт данных</AppText>
        <AppText secondary>
          Архив с вашими данными в формате JSON. Пароль, сессии и токены в него не входят. Файл можно сохранить или
          отправить через системное меню.
        </AppText>
        {exportError ? (
          <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
            {exportError}
          </AppText>
        ) : null}
        <Button
          title="Скачать мои данные"
          onPress={() => {
            void exportData();
          }}
          loading={exporting}
        />
      </Card>

      <Card>
        <AppText variant="headline">Удаление аккаунта</AppText>
        <AppText secondary>
          Удаляются все данные на сервере. Сначала можно выгрузить их архивом. Для подтверждения нужен текущий пароль.
        </AppText>
        <TextField
          label="Текущий пароль"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          autoComplete="current-password"
          textContentType="password"
        />
        <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md }}>
          <AppText style={{ flex: 1 }}>Я понимаю, что это необратимо</AppText>
          <Switch
            accessibilityLabel="Я понимаю, что это необратимо"
            value={confirmed}
            onValueChange={setConfirmed}
            trackColor={{ true: theme.danger }}
          />
        </View>
        {deleteError ? (
          <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
            {deleteError}
          </AppText>
        ) : null}
        <Button
          title="Удалить аккаунт"
          variant="destructive"
          disabled={password === "" || !confirmed}
          loading={deleting}
          onPress={confirmDelete}
        />
      </Card>
    </Screen>
  );
}
