import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { speechApi } from "@/api/speech-api";

import { settingsKey, speechConsentKey } from "./settings-keys";

export { speechConsentKey };

export function useSpeechConsent() {
  return useQuery({ queryKey: speechConsentKey, queryFn: () => speechApi.consent() });
}

export function useUpdateSpeechConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, enabled }: { version: number; enabled: boolean }) =>
      speechApi.updateConsent(version, enabled),
    onSuccess: (consent) => {
      queryClient.setQueryData(speechConsentKey, consent);
      return queryClient.invalidateQueries({ queryKey: settingsKey });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: speechConsentKey }),
  });
}
