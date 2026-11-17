import { randomUUID } from "expo-crypto";

import { httpClient, type HttpClient } from "./http-client";
import type { CreateReportInput, PageResponse, Report, ReportSummary } from "./reports-types";
import { formatEtag } from "./versions";

export function createReportsApi(client: HttpClient) {
  const key = (value: string | undefined) => ({ "Idempotency-Key": value ?? randomUUID() });

  return {
    create: (body: CreateReportInput, idempotencyKey?: string) =>
      client.request<Report>("/reports", { method: "POST", body, headers: key(idempotencyKey) }),

    list: async (): Promise<ReportSummary[]> =>
      (await client.request<PageResponse<ReportSummary>>("/reports?size=50")).items,

    get: (id: string) => client.request<Report>(`/reports/${id}`),

    cancel: (id: string, idempotencyKey?: string) =>
      client.request<Report>(`/reports/${id}/cancel`, { method: "POST", headers: key(idempotencyKey) }),

    remove: (id: string, version: number) =>
      client.request<void>(`/reports/${id}`, { method: "DELETE", headers: { "If-Match": formatEtag(version) } }),

    pdf: (id: string, includeAiOverview: boolean) =>
      client.requestBinary(`/reports/${id}/pdf?includeAiOverview=${includeAiOverview}`, { accept: "application/pdf" }),
  };
}

export type ReportsApi = ReturnType<typeof createReportsApi>;

export const reportsApi = createReportsApi(httpClient);
