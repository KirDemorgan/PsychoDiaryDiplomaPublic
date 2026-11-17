import type { Overview } from "./analytics-types";
import type { PageResponse } from "./ratings-types";
import type { TagCode } from "./tags-types";

export type { PageResponse };

export type ReportSection = "RATINGS" | "TAGS" | "MEDICATIONS" | "NOTES" | "AI_OVERVIEW";
export type ReportStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "CANCELLED";
export type OverviewMode = "AI" | "TEMPLATE" | "NONE";
export type EntrySelection = "ALL_VISIBLE" | "EXPLICIT" | "NONE";
export type MedicationSelection = "ALL" | "EXPLICIT" | "NONE";

export interface CreateReportInput {
  from: string;
  to: string;
  timeZone: string;
  displayName?: string;
  sections: ReportSection[];
  entrySelection: EntrySelection;
  entryIds?: string[];
  medicationSelection: MedicationSelection;
  medicationIds?: string[];
  comparisonTagCodes?: TagCode[];
}

export interface OverviewClaim {
  id: string;
  text: string;
  factIds: string[];
}

export interface ReportOverview {
  title: string;
  claims: OverviewClaim[];
  limitations: string[];
  provenance: { mode: OverviewMode; modelId: string | null };
}

export interface ReportSummary {
  id: string;
  from: string;
  to: string;
  timeZone: string;
  status: ReportStatus;
  stale: boolean;
  createdAt: string;
  version: number;
  overviewMode: OverviewMode;
  warnings: string[];
}

export interface Report extends ReportSummary {
  selection: CreateReportInput;
  overview: ReportOverview | null;
  previewData: {
    statistics: Overview | null;
    ratings: unknown[];
    entries: unknown[];
    medications: unknown[];
    comparisons: unknown[];
  } | null;
  errorCode: string | null;
}
