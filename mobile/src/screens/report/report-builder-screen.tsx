import { randomUUID } from "expo-crypto";
import { useRouter } from "expo-router";
import { useState } from "react";
import { StyleSheet, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import type { TagCode } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Chip } from "@/components/chip";
import { Screen } from "@/components/screen";
import { SegmentedPicker } from "@/components/segmented-picker";
import { TextField } from "@/components/text-field";
import { PERIOD_OPTIONS, type PeriodDays } from "@/features/analytics/period";
import {
  buildReportRequest,
  DEFAULT_OPTIONS,
  type ReportOptions,
} from "@/features/reports/build-request";
import { TAG_CODES, TAG_LABELS, tagColor } from "@/features/tags/tag-colors";
import { useCreateReport } from "@/hooks/use-reports";
import { useSettings } from "@/hooks/use-settings";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const SECTION_ROWS: { key: keyof ReportOptions; label: string }[] = [
  { key: "ratings", label: "Оценки и графики" },
  { key: "tags", label: "Подтверждённые теги и сравнение" },
  { key: "medications", label: "Препараты и журнал приёмов" },
  { key: "notes", label: "Записи дневника" },
];

export function ReportBuilderScreen() {
  const theme = useTheme();
  const router = useRouter();
  const settings = useSettings();
  const create = useCreateReport();
  const [idempotencyKey] = useState(randomUUID);
  const [days, setDays] = useState<PeriodDays>("30");
  const [displayName, setDisplayName] = useState("");
  const [options, setOptions] = useState<ReportOptions>(DEFAULT_OPTIONS);
  const [comparisonTags, setComparisonTags] = useState<TagCode[]>([]);
  const [error, setError] = useState<string | null>(null);

  const aiAllowed = settings.data?.aiEnabled === true;

  const toggleTag = (code: TagCode) =>
    setComparisonTags((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
    );

  const submit = async () => {
    setError(null);
    try {
      const report = await create.mutateAsync({
        body: buildReportRequest({
          days: Number(days),
          timeZone: deviceTimeZone(),
          displayName,
          options: { ...options, aiOverview: options.aiOverview && aiAllowed },
          comparisonTags,
        }),
        idempotencyKey,
      });
      router.replace({ pathname: "/report/[id]", params: { id: report.id } });
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  return (
    <Screen>
      <AppText variant="caption" secondary>
        Отчёт для обсуждения с врачом. Это не медицинское заключение: в нём нет диагнозов и рекомендаций по лечению.
      </AppText>

      <SegmentedPicker accessibilityLabel="Период отчёта" options={PERIOD_OPTIONS} value={days} onChange={setDays} />
      <TextField
        label="Имя или псевдоним в отчёте (необязательно)"
        value={displayName}
        onChangeText={setDisplayName}
      />

      <Card>
        <AppText variant="headline">Что включить</AppText>
        {SECTION_ROWS.map((row) => (
          <View key={row.key} style={styles.row}>
            <AppText style={styles.label}>{row.label}</AppText>
            <Switch
              accessibilityLabel={row.label}
              value={options[row.key]}
              onValueChange={(value) => setOptions((current) => ({ ...current, [row.key]: value }))}
              trackColor={{ true: theme.primary }}
            />
          </View>
        ))}
        <View style={styles.row}>
          <AppText style={styles.label}>AI-обзор наблюдений</AppText>
          <Switch
            accessibilityLabel="AI-обзор наблюдений"
            value={options.aiOverview && aiAllowed}
            disabled={!aiAllowed}
            onValueChange={(value) => setOptions((current) => ({ ...current, aiOverview: value }))}
            trackColor={{ true: theme.primary }}
          />
        </View>
        {!aiAllowed ? (
          <AppText variant="caption" secondary>
            AI-обзор недоступен: AI выключен в настройках.
          </AppText>
        ) : null}
        <AppText variant="caption" secondary>
          Скрытые из отчёта записи в PDF не попадают.
        </AppText>
      </Card>

      {options.tags ? (
        <Card>
          <AppText variant="headline">Сравнить дни с тегом и без</AppText>
          <AppText variant="caption" secondary>
            Сравнение показывается при достаточном числе дней в обеих группах.
          </AppText>
          <View style={styles.chips} accessibilityLabel="Теги для сравнения">
            {TAG_CODES.map((code) => (
              <Chip
                key={code}
                label={TAG_LABELS[code]}
                color={tagColor(code, theme)}
                selected={comparisonTags.includes(code)}
                onPress={() => toggleTag(code)}
              />
            ))}
          </View>
        </Card>
      ) : null}

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Button
        title="Сформировать отчёт"
        onPress={() => {
          void submit();
        }}
        loading={create.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  label: { flex: 1 },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
});
