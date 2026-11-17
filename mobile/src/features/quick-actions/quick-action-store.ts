import { create } from "zustand";

import type { QuickAction } from "@/lib/native-intent";

interface QuickActionState {
  pending: QuickAction | null;
  request: (action: QuickAction) => void;
  consume: () => QuickAction | null;
}

export const useQuickActionStore = create<QuickActionState>((set, get) => ({
  pending: null,
  request: (action) => set({ pending: action }),
  consume: () => {
    const { pending } = get();
    set({ pending: null });
    return pending;
  },
}));
