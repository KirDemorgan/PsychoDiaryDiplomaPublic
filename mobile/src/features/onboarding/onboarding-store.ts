import { create } from "zustand";

import { loadOnboardingCompleted, saveOnboardingCompleted } from "./onboarding-storage";

export type OnboardingStatus = "loading" | "pending" | "completed";

interface OnboardingState {
  status: OnboardingStatus;
  restore: () => Promise<void>;
  complete: () => Promise<void>;
}

export const useOnboardingStore = create<OnboardingState>((set) => ({
  status: "loading",
  restore: async () => {
    set({ status: (await loadOnboardingCompleted()) ? "completed" : "pending" });
  },
  complete: async () => {
    set({ status: "completed" });
    await saveOnboardingCompleted();
  },
}));
