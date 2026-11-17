import { useRouter } from "expo-router";
import { useEffect } from "react";

import type { SessionStatus } from "@/auth/session-store";

import { useQuickActionStore } from "./quick-action-store";

export function usePendingQuickAction(sessionStatus: SessionStatus): void {
  const router = useRouter();
  const pending = useQuickActionStore((state) => state.pending);

  useEffect(() => {
    if (sessionStatus !== "signedIn" || pending === null) {
      return;
    }
    useQuickActionStore.getState().consume();
    router.push("/entry/new");
  }, [sessionStatus, pending, router]);
}
