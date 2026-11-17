import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { entriesApi } from "@/api/entries-api";

export const entriesKey = ["entries"] as const;
export const entryKey = (id: string) => ["entry", id] as const;

export function useEntries(search: string) {
  const q = search.trim();
  return useInfiniteQuery({
    queryKey: [...entriesKey, { q }],
    queryFn: ({ pageParam }) => entriesApi.list({ q: q || undefined, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
}

export function useEntry(id: string) {
  return useQuery({ queryKey: entryKey(id), queryFn: () => entriesApi.get(id) });
}
