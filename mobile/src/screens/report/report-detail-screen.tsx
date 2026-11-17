import { useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Alert, StyleSheet, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import { reportsApi } from "@/api/reports-api";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { formatDateOnly } from "@/lib/format";
import { OVERVIEW_MODE_LABELS, STATUS_LABELS } from "@/features/reports/labels";
import { useCancelReport, useDeleteReport, useReport } from "@/hooks/use-reports";
import { PDF_FILE, shareFile } from "@/lib/share-file";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function ReportDetailScreen({ id }: { id: string }) {
  const theme = useTheme();
  const router = useRouter();
  const report = useReport(id);
  const cancel = useCancelReport(id);
  const remove = useDeleteReport(id);
  const [includeAi, setIncludeAi] = useState(true);
  const [sharing, setSharing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (report.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка отчёта" />
      </Screen>
    );
  }
  if (report.isError) {
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {describeError(report.error)}
        </AppText>
        <Button title="Повторить" variant="secondary" onPress={() => report.refetch()} />
      </Screen>
    );
  }

  const value = report.data;
  const working = value.status === "QUEUED" || value.status === "RUNNING";
  const ready = value.status === "READY";
  const claims = value.overview?.claims ?? [];
  const hasAi = value.overviewMode === "AI";

  const share = async () => {
    setError(null);
    setSharing(true);
    try {
      const bytes = await reportsApi.pdf(id, hasAi && includeAi);
      await shareFile(bytes, `diary-report-${value.from}-${value.to}.pdf`, PDF_FILE);
    } catch (failure) {
      setError(describeError(failure));
    } finally {
      setSharing(false);
    }
  };

  const confirmDelete = () =>
    Alert.alert("Удалить отчёт?", "Снимок данных отчёта будет удалён с сервера.", [
      { text: "Отмена", style: "cancel" },
      {
        text: "Удалить",
        style: "destructive",
        onPress: () => {
          remove.mutateAsync(value.version).then(
            () => router.back(),
            (failure: unknown) => setError(describeError(failure)),
          );
        },
      },
    ]);

  return (
    <Screen>
      <AppText variant="title">
        {formatDateOnly(value.from)} — {formatDateOnly(value.to)}
      </AppText>
      <AppText secondary>{STATUS_LABELS[value.status]}</AppText>

      {working ? (
        <Card style={styles.center}>
          <ActivityIndicator accessibilityLabel="Отчёт готовится" />
          <AppText secondary>Отчёт готовится…</AppText>
          <Button
            title="Отменить"
            variant="secondary"
            onPress={() => cancel.mutate()}
            loading={cancel.isPending}
          />
        </Card>
      ) : null}

      {value.status === "FAILED" || value.status === "CANCELLED" ? (
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {value.status === "CANCELLED" ? "Отчёт отменён." : "Не удалось подготовить отчёт. Создайте новый."}
        </AppText>
      ) : null}

      {value.stale ? (
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          Данные изменились после создания отчёта. Создайте новый отчёт, чтобы PDF совпадал с дневником.
        </AppText>
      ) : null}

      {ready ? (
        <>
          <Card>
            <AppText variant="headline">В отчёт вошло</AppText>
            <AppText>Оценок: {value.previewData?.ratings.length ?? 0}</AppText>
            <AppText>Записей: {value.previewData?.entries.length ?? 0}</AppText>
            <AppText>Препаратов: {value.previewData?.medications.length ?? 0}</AppText>
            <AppText>Сравнений дней: {value.previewData?.comparisons.length ?? 0}</AppText>
          </Card>

          {value.overview ? (
            <Card>
              <AppText variant="headline">AI-обзор наблюдений для обсуждения с врачом</AppText>
              <AppText variant="caption" secondary>
                {OVERVIEW_MODE_LABELS[value.overviewMode]}
              </AppText>
              {claims.map((claim) => (
                <AppText key={claim.id}>{claim.text}</AppText>
              ))}
              {value.overview.limitations.map((limitation) => (
                <AppText key={limitation} variant="caption" secondary>
                  {limitation}
                </AppText>
              ))}
            </Card>
          ) : null}

          {value.warnings.length > 0 ? (
            <Card>
              <AppText variant="headline">Замечания</AppText>
              {value.warnings.map((warning) => (
                <AppText key={warning} variant="callout">
                  {warning}
                </AppText>
              ))}
            </Card>
          ) : null}

          {hasAi ? (
            <View style={styles.row}>
              <AppText style={styles.label}>Включить AI-обзор в PDF</AppText>
              <Switch
                accessibilityLabel="Включить AI-обзор в PDF"
                value={includeAi}
                onValueChange={setIncludeAi}
                trackColor={{ true: theme.primary }}
              />
            </View>
          ) : null}

          <Button
            title="Поделиться PDF"
            onPress={() => {
              void share();
            }}
            disabled={value.stale}
            loading={sharing}
          />
        </>
      ) : null}

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      {working ? null : (
        <Button title="Удалить отчёт" variant="destructive" onPress={confirmDelete} loading={remove.isPending} />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { alignItems: "center" },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  label: { flex: 1 },
});
