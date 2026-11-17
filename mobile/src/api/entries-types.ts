import type { PageResponse } from "./ratings-types";
import type { AnalysisResponse, TagCategory, TagCode, TagPresence, TagSubject, TagTemporal } from "./tags-types";

export type { PageResponse };

export interface TagResponse {
  id: string;
  labelCode: TagCode;
  category: TagCategory;
  quote: string;
  start: number;
  end: number;
  presence: TagPresence;
  temporal: TagTemporal;
  subject: TagSubject;
  appliesToLocalDate: string | null;
  origin: "MANUAL" | "AI";
}

export interface EntrySummary {
  id: string;
  occurredAt: string;
  timeZone: string;
  localDateInRequestedZone: string | null;
  textPreview: string;
  revision: number;
  version: number;
  reportVisible: boolean;
  confirmedTags: string[];
  analysisStatus: string | null;
}

export interface EntryResponse {
  id: string;
  text: string;
  occurredAt: string;
  timeZone: string;
  revision: number;
  version: number;
  reportVisible: boolean;
  reviewComplete: boolean;
  tags: TagResponse[];
  latestAnalysis: AnalysisResponse | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEntryRequest {
  text: string;
  occurredAt: string;
  timeZone: string;
  reportVisible?: boolean;
}

export interface UpdateEntryRequest {
  text?: string;
  occurredAt?: string;
  timeZone?: string;
  reportVisible?: boolean;
}

export interface EntryListParams {
  q?: string;
  from?: string;
  to?: string;
  timeZone?: string;
  confirmedTag?: string;
  page?: number;
  size?: number;
}
