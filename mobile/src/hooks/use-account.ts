import { useMutation, useQueryClient } from "@tanstack/react-query";

import { accountApi } from "@/api/account-api";
import { useSessionStore } from "@/auth/session-store";

import { profileQueryKey } from "./use-profile";

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, body }: { version: number; body: { displayName?: string | null; timeZone?: string } }) =>
      accountApi.updateProfile(version, body),
    onSuccess: (user) => {
      useSessionStore.getState().setUser(user);
      queryClient.setQueryData(profileQueryKey, user);
    },
    onError: () => queryClient.invalidateQueries({ queryKey: profileQueryKey }),
  });
}
