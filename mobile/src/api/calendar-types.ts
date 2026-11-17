import type { EntrySummary } from "./entries-types";
import type { Occurrence } from "./medications-types";
import type { PageResponse, RatingResponse, RatingSlot } from "./ratings-types";
import type { TagCode } from "./tags-types";

export interface CalendarDay {
  date: string;
  ratings: Partial<Record<RatingSlot, RatingResponse>>;
  entryCount: number;
  reviewedEntryCount: number;
  pendingReviewCount: number;
  confirmedPresentTagCodes: TagCode[];
  ambiguousTagCodes: TagCode[];
  medication: { planned: number; taken: number; skipped: number; noData: number } | null;
}

export interface CalendarResponse {
  period: { from: string; to: string; timeZone: string };
  days: CalendarDay[];
  dataRevision: number;
}

export interface DayResponse {
  day: CalendarDay;
  entries: PageResponse<EntrySummary>;
  occurrences: Occurrence[];
  dataRevision: number;
}
