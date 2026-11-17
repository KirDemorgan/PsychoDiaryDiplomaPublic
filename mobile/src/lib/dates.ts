import type { RatingSlot } from "@/api/ratings-types";

const MORNING_ENDS_AT_HOUR = 11;
const DAY_ENDS_AT_HOUR = 17;

export function localDateIn(timeZone: string, now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

export function localHourIn(timeZone: string, now: Date = new Date()): number {
  const hour = new Intl.DateTimeFormat("en-GB", { timeZone, hour: "2-digit", hourCycle: "h23" }).format(now);
  return Number.parseInt(hour, 10);
}

export function slotForHour(hour: number): RatingSlot {
  if (hour < MORNING_ENDS_AT_HOUR) {
    return "MORNING";
  }
  return hour < DAY_ENDS_AT_HOUR ? "DAY" : "EVENING";
}

export function currentSlot(timeZone: string, now: Date = new Date()): RatingSlot {
  return slotForHour(localHourIn(timeZone, now));
}

export function currentTime(): Date {
  return new Date();
}

export function msUntil(isoInstant: string, now: Date = currentTime()): number {
  return Math.max(new Date(isoInstant).getTime() - now.getTime(), 0);
}
