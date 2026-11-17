import { randomUUID } from "expo-crypto";

import type { EntryResponse } from "./entries-types";
import { httpClient, type HttpClient } from "./http-client";
import type { AnalysisResponse, TagDefinitions, TagReviewRequest } from "./tags-types";
import { formatEtag } from "./versions";

export function createAnalysisApi(client: HttpClient) {
  return {
    start: (entryId: string, revision: number, idempotencyKey: string = randomUUID()) =>
      client.request<AnalysisResponse>(`/entries/${entryId}/analyses`, {
        method: "POST",
        body: { revision },
        headers: { "Idempotency-Key": idempotencyKey },
      }),

    get: (entryId: string, analysisId: string) =>
      client.request<AnalysisResponse>(`/entries/${entryId}/analyses/${analysisId}`),

    cancel: (entryId: string, analysisId: string, idempotencyKey: string = randomUUID()) =>
      client.request<AnalysisResponse>(`/entries/${entryId}/analyses/${analysisId}/cancel`, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
      }),

    definitions: () => client.request<TagDefinitions>("/tag-definitions"),

    submitReview: (
      entryId: string,
      version: number,
      body: TagReviewRequest,
      idempotencyKey: string = randomUUID(),
    ) =>
      client.request<EntryResponse>(`/entries/${entryId}/tag-review`, {
        method: "PUT",
        body,
        headers: { "Idempotency-Key": idempotencyKey, "If-Match": formatEtag(version) },
      }),
  };
}

export type AnalysisApi = ReturnType<typeof createAnalysisApi>;

export const analysisApi = createAnalysisApi(httpClient);
