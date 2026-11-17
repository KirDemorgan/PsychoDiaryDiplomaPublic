import { useEffect } from "react";

import type { SessionStatus } from "@/auth/session-store";

import { useOnboardingStore, type OnboardingStatus } from "./onboarding-store";

export function useOnboardingBootstrap(sessionStatus: SessionStatus): OnboardingStatus {
  const status = useOnboardingStore((state) => state.status);

  useEffect(() => {
    void useOnboardingStore.getState().restore();
  }, []);

  useEffect(() => {
    if (sessionStatus === "signedIn" && status === "pending") {
      void useOnboardingStore.getState().complete();
    }
  }, [sessionStatus, status]);

  return status;
}
