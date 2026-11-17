import { QueryClient, focusManager } from "@tanstack/react-query";
import { AppState } from "react-native";

import { isApiError } from "@/api/errors";

const MAX_RETRIES = 2;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) =>
          failureCount < MAX_RETRIES && (!isApiError(error) || error.status === 0 || error.status >= 500),
      },
    },
  });
}

export function bindFocusToAppState(): () => void {
  const subscription = AppState.addEventListener("change", (state) => focusManager.setFocused(state === "active"));
  return () => subscription.remove();
}
