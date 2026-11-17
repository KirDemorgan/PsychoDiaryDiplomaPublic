import type { TagCode } from "./tags-types";

export type Metric = "ANXIETY" | "MOOD" | "ENERGY";

export type Decimal = string | number | null;

export interface Period {
  from: string;
  to: string;
  timeZone: string;
}

export interface MetricCoverage {
  filled: number;
  expected: number;
  filledDays: number;
  totalDays: number;
}

export interface Coverage {
  metrics: Record<Metric, MetricCoverage>;
  filledSlots: number;
  expectedSlots: number;
  filledDays: number;
  totalDays: number;
}

export interface DailyValue {
  date: string;
  anxietyMean: Decimal;
  anxietySlotCount: number;
  moodMean: Decimal;
  moodSlotCount: number;
  energyMean: Decimal;
  energySlotCount: number;
}

export interface TagFrequency {
  code: TagCode;
  occurrences: number;
  days: number;
}

export interface MedicationAdherence {
  medicationId: string;
  medicationName: string;
  planned: number;
  taken: number;
  skipped: number;
  noData: number;
  takenRate: Decimal;
  markedCoverage: Decimal;
  unplanned: number;
}

export interface HighAnxietyDay {
  date: string;
  mean: Decimal;
  slotCount: number;
  threshold: number;
  rule: string;
}

export interface Overview {
  period: Period;
  dataRevision: number;
  coverage: Coverage;
  dailyValues: DailyValue[];
  tagFrequencies: TagFrequency[];
  medicationAdherence: MedicationAdherence[];
  highAnxietyDays: HighAnxietyDay[];
  ambiguousContext: { date: string; codes: TagCode[] }[];
  limitations: string[];
}

export interface ComparisonGroup {
  nDays: number;
  mean: Decimal;
  dates: string[];
}

export interface Comparison {
  tagCode: TagCode;
  metric: Metric;
  period: Period;
  status: "SUFFICIENT" | "INSUFFICIENT_DATA";
  withMention: ComparisonGroup;
  withoutMention: ComparisonGroup;
  excluded: { noRating: number; noEntry: number; incompleteContext: number; ambiguousContext: number };
  delta: Decimal;
  limitations: string[];
  dataRevision: number;
}
