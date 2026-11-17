import { Link, useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";

import type { Metric } from "@/api/analytics-types";
import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Chip } from "@/components/chip";
import { LineChart } from "@/components/line-chart";
import { Screen } from "@/components/screen";
import { ScreenTitle } from "@/components/screen-title";
import { SegmentedPicker } from "@/components/segmented-picker";
import { eachDate, periodRange, PERIOD_OPTIONS, shortDate, type PeriodDays } from "@/features/analytics/period";
import {
  dailySeries,
  formatDecimal,
  formatPercent,
  isFilledDay,
  METRIC_LABELS,
  METRIC_OPTIONS,
  metricColor,
} from "@/features/analytics/metrics";
import { TAG_LABELS } from "@/features/tags/tag-colors";
import { useOverview } from "@/hooks/use-analytics";
import { joinSentences } from "@/lib/format";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const HIGH_ANXIETY_THRESHOLD = 7;

export function DynamicsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [timeZone] = useState(deviceTimeZone);
  const [days, setDays] = useState<PeriodDays>("7");
  const [metric, setMetric] = useState<Metric>("ANXIETY");
  const range = periodRange(Number(days), timeZone);
  const overview = useOverview(range.from, range.to, timeZone);
  const dates = eachDate(range.from, range.to);

  const data = overview.data;
  const series = data ? dailySeries(data.dailyValues, metric, dates) : [];
  const hasValues = series.some((point) => point.value !== null);
  const filledDates = new Set(data?.dailyValues.filter(isFilledDay).map((day) => day.date));
  const usedTags = data?.tagFrequencies.filter((tag) => tag.days > 0) ?? [];

  return (
    <Screen>
      <ScreenTitle>Динамика</ScreenTitle>
      <SegmentedPicker accessibilityLabel="Период" options={PERIOD_OPTIONS} value={days} onChange={setDays} />

      <View style={styles.chips} accessibilityRole="radiogroup" accessibilityLabel="Показатель">
        {METRIC_OPTIONS.map((option) => (
          <Chip
            key={option.value}
            label={option.label}
            color={metricColor(option.value, theme)}
            selected={metric === option.value}
            onPress={() => setMetric(option.value)}
          />
        ))}
      </View>

      {overview.isPending ? <ActivityIndicator accessibilityLabel="Загрузка динамики" /> : null}

      {overview.isError ? (
        <Card>
          <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
            {describeError(overview.error)}
          </AppText>
          <Button title="Повторить" variant="secondary" onPress={() => overview.refetch()} />
        </Card>
      ) : null}

      {data ? (
        <>
          <Card>
            <AppText variant="headline">{METRIC_LABELS[metric]}</AppText>
            <LineChart
              values={series.map((point) => point.value)}
              labels={series.map((point) => shortDate(point.date))}
              color={metricColor(metric, theme)}
              threshold={metric === "ANXIETY" ? HIGH_ANXIETY_THRESHOLD : undefined}
              accessibilityLabel={`${METRIC_LABELS[metric]} за ${dates.length} дн.`}
            />
            {hasValues ? (
              <AppText variant="caption" secondary>
                Пропуски не интерполируются: линия прерывается там, где оценок нет.
              </AppText>
            ) : (
              <AppText variant="caption" secondary>
                За выбранный период оценок нет.
              </AppText>
            )}
          </Card>

          <Card>
            <AppText variant="headline">
              Заполнено {data.coverage.filledDays} из {data.coverage.totalDays} дней
            </AppText>
            <View style={styles.dots} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
              {dates.map((date) => (
                <View
                  key={date}
                  style={[styles.dot, { backgroundColor: filledDates.has(date) ? theme.primary : theme.surfaceMuted }]}
                />
              ))}
            </View>
          </Card>

          <Card>
            <AppText variant="headline">Подтверждённые теги</AppText>
            {usedTags.length === 0 ? <AppText secondary>Подтверждённых тегов за период нет.</AppText> : null}
            {usedTags.map((tag) => (
              <Link
                key={tag.code}
                href={{
                  pathname: "/dynamics/comparison",
                  params: { code: tag.code, metric, from: range.from, to: range.to },
                }}
                asChild
              >
                <Pressable accessibilityRole="link" style={styles.row}>
                  <AppText>{TAG_LABELS[tag.code]}</AppText>
                  <AppText secondary>{tag.days} дн.</AppText>
                </Pressable>
              </Link>
            ))}
          </Card>

          {data.medicationAdherence.length > 0 ? (
            <Card>
              <AppText variant="headline">Приём препаратов</AppText>
              {data.medicationAdherence.map((item) => (
                <View key={item.medicationId} style={{ gap: 2 }}>
                  <AppText>{item.medicationName}</AppText>
                  <AppText variant="caption" secondary>
                    Принято {item.taken} из {item.planned} плановых ({formatPercent(item.takenRate)}). Отмечено{" "}
                    {formatPercent(item.markedCoverage)} доз; без отметки {item.noData}.
                  </AppText>
                </View>
              ))}
            </Card>
          ) : null}

          {data.highAnxietyDays.length > 0 ? (
            <Card>
              <AppText variant="headline">Дни с высокой тревожностью</AppText>
              {data.highAnxietyDays.map((item) => (
                <AppText key={item.date} variant="callout">
                  {shortDate(item.date)} · среднее {formatDecimal(item.mean)} из 10
                </AppText>
              ))}
              <AppText variant="caption" secondary>
                Это правило интерфейса (среднее не ниже {HIGH_ANXIETY_THRESHOLD} из 10), а не клинический порог.
              </AppText>
            </Card>
          ) : null}

          <Button title="Отчёт для врача" onPress={() => router.push("/report/new")} />
          <Button title="История отчётов" variant="secondary" onPress={() => router.push("/report")} />

          {data.limitations.length > 0 ? (
            <AppText variant="caption" secondary>
              {joinSentences(data.limitations)}
            </AppText>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  dots: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  dot: { width: 14, height: 14, borderRadius: 7 },
  row: { flexDirection: "row", justifyContent: "space-between", minHeight: 36, alignItems: "center" },
});
