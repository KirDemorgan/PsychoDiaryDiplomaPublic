import { randomUUID } from "expo-crypto";

import { httpClient, type HttpClient } from "./http-client";
import type { PageResponse, RatingResponse, RatingSlot, RatingValues } from "./ratings-types";
import { formatEtag } from "./versions";

export interface SaveRatingInput {
  date: string;
  slot: RatingSlot;
  values: RatingValues;
  timeZone: string;
  existing?: Pick<RatingResponse, "version"> | null;
  idempotencyKey?: string;
}

export interface DeleteRatingInput {
  date: string;
  slot: RatingSlot;
  existing: Pick<RatingResponse, "version">;
}

const MAX_PAGE_SIZE = 100;

export function createRatingsApi(client: HttpClient) {
  return {
    listForRange: async (from: string, to: string): Promise<RatingResponse[]> => {
      const query = new URLSearchParams({ from, to, size: String(MAX_PAGE_SIZE) });
      const page = await client.request<PageResponse<RatingResponse>>(`/ratings?${query}`);
      return page.items;
    },

    save: ({ date, slot, values, timeZone, existing, idempotencyKey }: SaveRatingInput) =>
      client.request<RatingResponse>(`/ratings/${date}/${slot}`, {
        method: "PUT",
        body: { ...values, timeZone },
        headers: {
          "Idempotency-Key": idempotencyKey ?? randomUUID(),
          ...(existing ? { "If-Match": formatEtag(existing.version) } : {}),
        },
      }),

    remove: ({ date, slot, existing }: DeleteRatingInput) =>
      client.request<void>(`/ratings/${date}/${slot}`, {
        method: "DELETE",
        headers: { "If-Match": formatEtag(existing.version) },
      }),
  };
}

export type RatingsApi = ReturnType<typeof createRatingsApi>;

export const ratingsApi = createRatingsApi(httpClient);
