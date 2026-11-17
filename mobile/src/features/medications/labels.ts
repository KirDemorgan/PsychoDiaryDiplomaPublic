import type { DayOfWeek, DoseUnit, IntakeStatus } from "@/api/medications-types";

export const DAYS: readonly DayOfWeek[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

export const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: "Пн",
  TUESDAY: "Вт",
  WEDNESDAY: "Ср",
  THURSDAY: "Чт",
  FRIDAY: "Пт",
  SATURDAY: "Сб",
  SUNDAY: "Вс",
};

export const UNITS: readonly DoseUnit[] = ["MG", "G", "ML", "TABLET", "DROP", "UNIT"];

export const UNIT_LABELS: Record<DoseUnit, string> = {
  MG: "мг",
  G: "г",
  ML: "мл",
  TABLET: "таб.",
  DROP: "капли",
  UNIT: "ед.",
};

export const STATUS_LABELS: Record<IntakeStatus, string> = {
  TAKEN: "Принято",
  SKIPPED: "Пропущено",
  NO_DATA: "Нет отметки",
};
