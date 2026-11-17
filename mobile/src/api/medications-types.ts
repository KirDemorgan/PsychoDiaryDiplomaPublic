export type DoseUnit = "MG" | "G" | "ML" | "TABLET" | "DROP" | "UNIT";
export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
export type IntakeStatus = "TAKEN" | "SKIPPED" | "NO_DATA";
export type IntakeKind = "PLANNED" | "UNPLANNED";

export interface ScheduleSlot {
  id: string;
  localTime: string;
  daysOfWeek: DayOfWeek[];
  dose: string | number;
  unit: DoseUnit;
}

export interface ScheduleVersion {
  id: string;
  medicationId: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  treatmentEndsOn: string | null;
  timeZone: string;
  slots: ScheduleSlot[];
  createdAt: string;
  medicationVersion: number;
}

export interface Medication {
  id: string;
  name: string;
  form: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  currentSchedule: ScheduleVersion | null;
}

export interface Intake {
  id: string;
  medicationId: string;
  scheduleSlotId: string | null;
  plannedAt: string | null;
  kind: IntakeKind;
  status: IntakeStatus;
  actualAt: string | null;
  actualDose: string | number | null;
  unit: DoseUnit | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Occurrence {
  occurrenceId: string;
  medicationId: string;
  medicationName: string;
  scheduleVersionId: string;
  scheduleSlotId: string;
  plannedAt: string;
  plannedDose: string | number;
  unit: DoseUnit;
  timeZone: string;
  status: IntakeStatus;
  intake: Intake | null;
}

export interface OccurrenceList {
  from: string;
  to: string;
  timeZone: string;
  items: Occurrence[];
}

export interface ScheduleSlotInput {
  localTime: string;
  daysOfWeek: DayOfWeek[];
  dose: number;
  unit: DoseUnit;
}

export interface CreateScheduleInput {
  effectiveFrom: string;
  treatmentEndsOn: string | null;
  timeZone: string;
  slots: ScheduleSlotInput[];
}

export type CreateIntakeInput =
  | { kind: "PLANNED"; occurrenceId: string; status: "TAKEN" | "SKIPPED"; actualAt?: string; actualDose?: number; unit?: DoseUnit }
  | { kind: "UNPLANNED"; medicationId: string; status: "TAKEN"; actualAt: string; actualDose: number; unit: DoseUnit };
