import { localDateIn } from "@/lib/dates";

const DAY_MS = 24 * 60 * 60 * 1000;

export const PERIOD_OPTIONS = [
  { value: "7", label: "7 дней" },
  { value: "14", label: "14 дней" },
  { value: "30", label: "30 дней" },
] as const;

export type PeriodDays = (typeof PERIOD_OPTIONS)[number]["value"];

export function periodRange(days: number, timeZone: string, now: Date = new Date()) {
  return {
    from: localDateIn(timeZone, new Date(now.getTime() - (days - 1) * DAY_MS)),
    to: localDateIn(timeZone, now),
  };
}

export function eachDate(from: string, to: string): string[] {
  const dates: string[] = [];
  const cursor = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  while (cursor <= end) {
    dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return dates;
}

export function shortDate(localDate: string): string {
  const [, month, day] = localDate.split("-");
  return `${day}.${month}`;
}
