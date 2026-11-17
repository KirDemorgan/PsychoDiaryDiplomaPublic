import { useMutation, useQueryClient } from "@tanstack/react-query";

import { entriesApi } from "@/api/entries-api";
import type { CreateEntryRequest, UpdateEntryRequest } from "@/api/entries-types";

import { entriesKey, entryKey } from "./use-entries";

export function useCreateEntry() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateEntryRequest; idempotencyKey: string }) =>
      entriesApi.create(body, idempotencyKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: entriesKey }),
  });
}

export function useUpdateEntry(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, body }: { version: number; body: UpdateEntryRequest }) =>
      entriesApi.update(id, version, body),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: entriesKey }),
        queryClient.invalidateQueries({ queryKey: entryKey(id) }),
      ]),
  });
}

export function useDeleteEntry(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (version: number) => entriesApi.remove(id, version),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: entryKey(id) });
      return queryClient.invalidateQueries({ queryKey: entriesKey });
    },
  });
}
