import type { Comparison, Metric, Overview } from "./analytics-types";
import { httpClient, type HttpClient } from "./http-client";
import type { TagCode } from "./tags-types";

export function createAnalyticsApi(client: HttpClient) {
  return {
    overview: (from: string, to: string, timeZone: string) =>
      client.request<Overview>(`/analytics/overview?${new URLSearchParams({ from, to, timeZone })}`),

    comparison: (from: string, to: string, timeZone: string, tagCode: TagCode, metric: Metric) =>
      client.request<Comparison>(
        `/analytics/tag-comparisons?${new URLSearchParams({ from, to, timeZone, tagCode, metric })}`,
      ),
  };
}

export type AnalyticsApi = ReturnType<typeof createAnalyticsApi>;

export const analyticsApi = createAnalyticsApi(httpClient);
