import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { analysisApi } from "@/api/analysis-api";
import type { TagReviewRequest } from "@/api/tags-types";

import { entriesKey, entryKey } from "./use-entries";

export const ANALYSIS_POLL_MS = 2000;

export const analysisKey = (entryId: string, analysisId: string) => ["analysis", entryId, analysisId] as const;

export function useAnalysis(entryId: string, analysisId: string | undefined) {
  return useQuery({
    queryKey: analysisKey(entryId, analysisId ?? ""),
    queryFn: () => analysisApi.get(entryId, analysisId as string),
    enabled: analysisId !== undefined,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "QUEUED" || status === "RUNNING" ? ANALYSIS_POLL_MS : false;
    },
  });
}

export function useStartAnalysis(entryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ revision, idempotencyKey }: { revision: number; idempotencyKey?: string }) =>
      analysisApi.start(entryId, revision, idempotencyKey),
    onSuccess: (analysis) => {
      queryClient.setQueryData(analysisKey(entryId, analysis.id), analysis);
      return queryClient.invalidateQueries({ queryKey: entryKey(entryId) });
    },
  });
}

export function useSubmitTagReview(entryId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      version,
      body,
      idempotencyKey,
    }: {
      version: number;
      body: TagReviewRequest;
      idempotencyKey?: string;
    }) => analysisApi.submitReview(entryId, version, body, idempotencyKey),
    onSuccess: (entry) => {
      queryClient.setQueryData(entryKey(entryId), entry);
      return queryClient.invalidateQueries({ queryKey: entriesKey });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: entryKey(entryId) }),
  });
}
