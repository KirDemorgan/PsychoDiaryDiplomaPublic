import { useQuery } from "@tanstack/react-query";

import { authApi } from "@/api/auth-api";

export const profileQueryKey = ["profile"] as const;

export function useProfile() {
  return useQuery({ queryKey: profileQueryKey, queryFn: () => authApi.me() });
}
