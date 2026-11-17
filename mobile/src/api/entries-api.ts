import { randomUUID } from "expo-crypto";

import type {
  CreateEntryRequest,
  EntryListParams,
  EntryResponse,
  EntrySummary,
  PageResponse,
  UpdateEntryRequest,
} from "./entries-types";
import { httpClient, type HttpClient } from "./http-client";
import { formatEtag } from "./versions";

const DEFAULT_PAGE_SIZE = 20;

function listQuery(params: EntryListParams): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries({ size: DEFAULT_PAGE_SIZE, ...params })) {
    if (value !== undefined && value !== "") {
      query.set(key, String(value));
    }
  }
  return query.toString();
}

export function createEntriesApi(client: HttpClient) {
  return {
    list: (params: EntryListParams = {}) =>
      client.request<PageResponse<EntrySummary>>(`/entries?${listQuery(params)}`),

    get: (id: string) => client.request<EntryResponse>(`/entries/${id}`),

    create: (body: CreateEntryRequest, idempotencyKey: string = randomUUID()) =>
      client.request<EntryResponse>("/entries", {
        method: "POST",
        body,
        headers: { "Idempotency-Key": idempotencyKey },
      }),

    update: (id: string, version: number, body: UpdateEntryRequest) =>
      client.request<EntryResponse>(`/entries/${id}`, {
        method: "PATCH",
        body,
        headers: { "If-Match": formatEtag(version) },
      }),

    remove: (id: string, version: number) =>
      client.request<void>(`/entries/${id}`, {
        method: "DELETE",
        headers: { "If-Match": formatEtag(version) },
      }),
  };
}

export type EntriesApi = ReturnType<typeof createEntriesApi>;

export const entriesApi = createEntriesApi(httpClient);
