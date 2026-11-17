import { useQuery } from "@tanstack/react-query";

import { ratingsApi } from "@/api/ratings-api";

export const dayRatingsKey = (date: string) => ["ratings", date] as const;

export function useDayRatings(date: string) {
  return useQuery({
    queryKey: dayRatingsKey(date),
    queryFn: () => ratingsApi.listForRange(date, date),
  });
}
