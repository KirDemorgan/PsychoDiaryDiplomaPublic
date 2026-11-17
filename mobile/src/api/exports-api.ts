import { randomUUID } from "expo-crypto";

import { httpClient, type HttpClient } from "./http-client";

export interface ExportJob {
  id: string;
  status: "READY" | "EXPIRED";
  createdAt: string;
  expiresAt: string;
  errorCode: string | null;
}

export function createExportsApi(client: HttpClient) {
  return {
    create: (idempotencyKey: string = randomUUID()) =>
      client.request<ExportJob>("/data-exports", { method: "POST", headers: { "Idempotency-Key": idempotencyKey } }),

    file: (id: string) => client.requestBinary(`/data-exports/${id}/file`, { accept: "application/zip" }),
  };
}

export type ExportsApi = ReturnType<typeof createExportsApi>;

export const exportsApi = createExportsApi(httpClient);
