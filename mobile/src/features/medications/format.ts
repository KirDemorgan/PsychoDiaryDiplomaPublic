import type { DayOfWeek, DoseUnit, ScheduleSlot, ScheduleVersion } from "@/api/medications-types";

import { DAYS, DAY_LABELS, UNIT_LABELS } from "./labels";

export function formatDose(dose: string | number, unit: DoseUnit): string {
  const value = Number(dose);
  const text = Number.isFinite(value) ? String(value).replace(".", ",") : String(dose);
  return `${text} ${UNIT_LABELS[unit]}`;
}

export function formatDays(days: readonly DayOfWeek[]): string {
  if (days.length === DAYS.length) {
    return "Каждый день";
  }
  return DAYS.filter((day) => days.includes(day))
    .map((day) => DAY_LABELS[day])
    .join(", ");
}

export function formatClock(localTime: string): string {
  return localTime.slice(0, 5);
}

export function describeSlot(slot: Pick<ScheduleSlot, "localTime" | "daysOfWeek" | "dose" | "unit">): string {
  return `${formatClock(slot.localTime)} · ${formatDose(slot.dose, slot.unit)} · ${formatDays(slot.daysOfWeek)}`;
}

export function formatPlannedTime(isoInstant: string, timeZone: string): string {
  return new Intl.DateTimeFormat("ru-RU", { timeZone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(
    new Date(isoInstant),
  );
}

export function upcomingSchedule(history: readonly ScheduleVersion[] | undefined, now: Date): ScheduleVersion | null {
  const future = (history ?? []).filter((version) => new Date(version.effectiveFrom) > now);
  return future.length === 0
    ? null
    : future.reduce((latest, version) => (version.effectiveFrom > latest.effectiveFrom ? version : latest));
}
