import { httpClient, type HttpClient } from "./http-client";
import type { UserResponse } from "./types";
import { formatEtag } from "./versions";

export function createAccountApi(client: HttpClient) {
  return {
    updateProfile: (version: number, body: { displayName?: string | null; timeZone?: string }) =>
      client.request<UserResponse>("/me", { method: "PATCH", body, headers: { "If-Match": formatEtag(version) } }),

    deleteAccount: (currentPassword: string) =>
      client.request<void>("/me", { method: "DELETE", body: { currentPassword, confirm: true } }),
  };
}

export type AccountApi = ReturnType<typeof createAccountApi>;

export const accountApi = createAccountApi(httpClient);
