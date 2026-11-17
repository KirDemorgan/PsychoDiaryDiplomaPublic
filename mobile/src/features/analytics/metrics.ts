import type { DailyValue, Decimal, Metric } from "@/api/analytics-types";
import type { Palette } from "@/theme/colors";

export const METRICS: readonly Metric[] = ["ANXIETY", "MOOD", "ENERGY"];

export const METRIC_LABELS: Record<Metric, string> = {
  ANXIETY: "Тревожность",
  MOOD: "Настроение",
  ENERGY: "Энергия",
};

export const METRIC_OPTIONS = METRICS.map((value) => ({ value, label: METRIC_LABELS[value] }));

export function metricColor(metric: Metric, palette: Palette): string {
  return { ANXIETY: palette.anxiety, MOOD: palette.mood, ENERGY: palette.energy }[metric];
}

export function toNumber(value: Decimal | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function dailyMean(day: DailyValue, metric: Metric): number | null {
  return toNumber({ ANXIETY: day.anxietyMean, MOOD: day.moodMean, ENERGY: day.energyMean }[metric]);
}

export function dailySeries(dailyValues: readonly DailyValue[], metric: Metric, dates: readonly string[]) {
  const byDate = new Map(dailyValues.map((day) => [day.date, dailyMean(day, metric)]));
  return dates.map((date) => ({ date, value: byDate.get(date) ?? null }));
}

export function isFilledDay(day: DailyValue): boolean {
  return day.anxietySlotCount + day.moodSlotCount + day.energySlotCount > 0;
}

export function formatDecimal(value: Decimal | undefined, digits = 1): string {
  const parsed = toNumber(value);
  return parsed === null ? "—" : parsed.toFixed(digits).replace(".", ",");
}

export function formatPercent(value: Decimal | undefined): string {
  const parsed = toNumber(value);
  return parsed === null ? "—" : `${Math.round(parsed * 100)}%`;
}
