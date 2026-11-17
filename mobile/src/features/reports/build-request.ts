import type { CreateReportInput, ReportSection } from "@/api/reports-types";
import type { TagCode } from "@/api/tags-types";
import { periodRange } from "@/features/analytics/period";

export const MAX_COMPARISON_TAGS = 12;

export interface ReportOptions {
  ratings: boolean;
  tags: boolean;
  medications: boolean;
  notes: boolean;
  aiOverview: boolean;
}

export const DEFAULT_OPTIONS: ReportOptions = {
  ratings: true,
  tags: true,
  medications: true,
  notes: true,
  aiOverview: false,
};

export interface BuildReportParams {
  days: number;
  timeZone: string;
  displayName: string;
  options: ReportOptions;
  comparisonTags: readonly TagCode[];
  now?: Date;
}

export function buildReportRequest(params: BuildReportParams): CreateReportInput {
  const { days, timeZone, displayName, options, comparisonTags, now } = params;
  const range = periodRange(days, timeZone, now);
  const sections: ReportSection[] = [];
  if (options.ratings) sections.push("RATINGS");
  if (options.tags) sections.push("TAGS");
  if (options.medications) sections.push("MEDICATIONS");
  if (options.notes) sections.push("NOTES");
  if (options.aiOverview) sections.push("AI_OVERVIEW");

  const name = displayName.trim();
  return {
    from: range.from,
    to: range.to,
    timeZone,
    ...(name ? { displayName: name } : {}),
    sections,
    entrySelection: options.notes ? "ALL_VISIBLE" : "NONE",
    medicationSelection: options.medications ? "ALL" : "NONE",
    comparisonTagCodes: options.tags ? comparisonTags.slice(0, MAX_COMPARISON_TAGS) : [],
  };
}
