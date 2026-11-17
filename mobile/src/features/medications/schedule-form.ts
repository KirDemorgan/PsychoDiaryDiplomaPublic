import type { CreateScheduleInput, DayOfWeek, DoseUnit, ScheduleSlotInput } from "@/api/medications-types";

import { DAYS } from "./labels";

export interface SlotDraft {
  key: string;
  time: string;
  days: DayOfWeek[];
  dose: string;
  unit: DoseUnit;
}

const DOSE_PATTERN = /^\d{1,6}([.,]\d{1,3})?$/;

export function newSlotDraft(key: string): SlotDraft {
  return { key, time: "09:00", days: [...DAYS], dose: "", unit: "TABLET" };
}

export function parseDose(text: string): number {
  return Number(normalizeDose(text));
}

export function normalizeDose(text: string): string {
  return text.trim().replace(",", ".");
}

export function isDoseValid(text: string): boolean {
  const normalized = normalizeDose(text);
  return DOSE_PATTERN.test(normalized) && Number(normalized) > 0;
}

export function validateSlots(slots: readonly SlotDraft[]): string | null {
  if (slots.length === 0) {
    return "Добавьте хотя бы один приём";
  }
  for (const slot of slots) {
    if (slot.days.length === 0) {
      return "Выберите дни приёма";
    }
    if (!isDoseValid(slot.dose)) {
      return "Укажите дозу числом больше нуля";
    }
  }
  return null;
}

export function toScheduleInput(
  slots: readonly SlotDraft[],
  effectiveFrom: string,
  timeZone: string,
  treatmentEndsOn: string | null,
): CreateScheduleInput {
  const mapped: ScheduleSlotInput[] = slots.map((slot) => ({
    localTime: slot.time,
    daysOfWeek: DAYS.filter((day) => slot.days.includes(day)),
    dose: parseDose(slot.dose),
    unit: slot.unit,
  }));
  return { effectiveFrom, treatmentEndsOn, timeZone, slots: mapped };
}

export function timeToDate(time: string, base: Date = new Date()): Date {
  const [hours, minutes] = time.split(":").map(Number);
  const date = new Date(base);
  date.setHours(hours, minutes, 0, 0);
  return date;
}

export function dateToTime(date: Date): string {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
