import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { reportsApi } from "@/api/reports-api";
import type { CreateReportInput } from "@/api/reports-types";

export const REPORT_POLL_MS = 2000;

export const reportsKey = ["reports"] as const;
export const reportKey = (id: string) => ["report", id] as const;

export function useReports() {
  return useQuery({ queryKey: reportsKey, queryFn: () => reportsApi.list() });
}

export function useReport(id: string) {
  return useQuery({
    queryKey: reportKey(id),
    queryFn: () => reportsApi.get(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "QUEUED" || status === "RUNNING" ? REPORT_POLL_MS : false;
    },
  });
}

export function useCreateReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateReportInput; idempotencyKey?: string }) =>
      reportsApi.create(body, idempotencyKey),
    onSuccess: (report) => {
      queryClient.setQueryData(reportKey(report.id), report);
      return queryClient.invalidateQueries({ queryKey: reportsKey });
    },
  });
}

export function useCancelReport(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => reportsApi.cancel(id),
    onSuccess: (report) => queryClient.setQueryData(reportKey(id), report),
    onSettled: () => queryClient.invalidateQueries({ queryKey: reportsKey }),
  });
}

export function useDeleteReport(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (version: number) => reportsApi.remove(id, version),
    onSuccess: () => queryClient.removeQueries({ queryKey: reportKey(id) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: reportsKey }),
  });
}
