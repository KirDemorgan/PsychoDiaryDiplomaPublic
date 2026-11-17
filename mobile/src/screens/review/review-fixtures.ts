import type { EntryResponse } from "@/api/entries-types";
import type { AnalysisResponse, Candidate } from "@/api/tags-types";

export const candidates: Candidate[] = [
  {
    candidateId: "c1",
    labelCode: "SLEEP_DEFICIT",
    category: "CONTEXT",
    quote: "Почти не спал",
    start: 0,
    end: 13,
    presence: "PRESENT",
    temporal: "CURRENT",
    subject: "SELF",
  },
  {
    candidateId: "c2",
    labelCode: "ANXIETY",
    category: "STATE",
    quote: "тревожился",
    start: 29,
    end: 39,
    presence: "PRESENT",
    temporal: "CURRENT",
    subject: "SELF",
  },
];

export const baseEntry: EntryResponse = {
  id: "e1",
  text: "Почти не спал. Перед встречей тревожился.",
  occurredAt: "2026-10-18T09:30:00Z",
  timeZone: "UTC",
  revision: 2,
  version: 5,
  reportVisible: true,
  reviewComplete: false,
  tags: [],
  latestAnalysis: null,
  createdAt: "2026-10-18T09:30:00Z",
  updatedAt: "2026-10-18T09:30:00Z",
};

export const analysis = (overrides: Partial<AnalysisResponse> = {}): AnalysisResponse => ({
  id: "a1",
  entryId: "e1",
  revision: 2,
  status: "SUCCEEDED",
  attempt: 1,
  createdAt: "2026-10-18T09:31:00Z",
  finishedAt: "2026-10-18T09:31:05Z",
  errorCode: null,
  result: { candidates },
  modelId: "model",
  promptVersion: "v2",
  schemaVersion: "v1",
  ...overrides,
});
