import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { settingsApi } from "@/api/settings-api";
import type { UpdateSettingsRequest } from "@/api/settings-types";

import { settingsKey, speechConsentKey } from "./settings-keys";

export { settingsKey };

export function useSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => settingsApi.get() });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, body }: { version: number; body: UpdateSettingsRequest }) =>
      settingsApi.update(version, body),
    onSuccess: (settings) => {
      queryClient.setQueryData(settingsKey, settings);
      return queryClient.invalidateQueries({ queryKey: speechConsentKey });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: settingsKey }),
  });
}
