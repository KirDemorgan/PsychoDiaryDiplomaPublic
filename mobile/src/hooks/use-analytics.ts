import { useQuery } from "@tanstack/react-query";

import { analyticsApi } from "@/api/analytics-api";
import type { Metric } from "@/api/analytics-types";
import type { TagCode } from "@/api/tags-types";

export function useOverview(from: string, to: string, timeZone: string) {
  return useQuery({
    queryKey: ["analytics", "overview", from, to, timeZone],
    queryFn: () => analyticsApi.overview(from, to, timeZone),
  });
}

export function useComparison(from: string, to: string, timeZone: string, tagCode: TagCode, metric: Metric) {
  return useQuery({
    queryKey: ["analytics", "comparison", from, to, timeZone, tagCode, metric],
    queryFn: () => analyticsApi.comparison(from, to, timeZone, tagCode, metric),
  });
}
