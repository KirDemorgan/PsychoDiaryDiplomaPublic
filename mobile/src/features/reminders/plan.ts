import type { Occurrence } from "@/api/medications-types";
import { formatDose } from "@/features/medications/format";

export const MAX_PENDING_REMINDERS = 60;

export interface PlannedReminder {
  id: string;
  at: Date;
  title: string;
  body: string;
}

export interface PlanOptions {
  now: Date;
  neutralText: boolean;
  limit?: number;
}

export function planReminders(occurrences: readonly Occurrence[], options: PlanOptions): PlannedReminder[] {
  const limit = options.limit ?? MAX_PENDING_REMINDERS;
  return occurrences
    .filter((occurrence) => occurrence.status === "NO_DATA" && new Date(occurrence.plannedAt) > options.now)
    .sort((left, right) => left.plannedAt.localeCompare(right.plannedAt))
    .slice(0, limit)
    .map((occurrence) => ({
      id: occurrence.occurrenceId,
      at: new Date(occurrence.plannedAt),
      title: options.neutralText ? "Напоминание" : occurrence.medicationName,
      body: options.neutralText
        ? "Пора отметить запланированное действие."
        : `Время приёма: ${formatDose(occurrence.plannedDose, occurrence.unit)}`,
    }));
}
