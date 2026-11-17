import { httpClient, type HttpClient } from "./http-client";
import type { UpdateSettingsRequest, UserSettings } from "./settings-types";
import { formatEtag } from "./versions";

export function createSettingsApi(client: HttpClient) {
  return {
    get: () => client.request<UserSettings>("/me/settings"),

    update: (version: number, body: UpdateSettingsRequest) =>
      client.request<UserSettings>("/me/settings", {
        method: "PATCH",
        body,
        headers: { "If-Match": formatEtag(version) },
      }),
  };
}

export type SettingsApi = ReturnType<typeof createSettingsApi>;

export const settingsApi = createSettingsApi(httpClient);
