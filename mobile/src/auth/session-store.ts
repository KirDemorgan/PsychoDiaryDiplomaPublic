import { create } from "zustand";

import type { UserResponse } from "@/api/types";

export type SessionStatus = "restoring" | "signedOut" | "signedIn";

interface SessionState {
  status: SessionStatus;
  user: UserResponse | null;
  setSignedIn: (user: UserResponse) => void;
  setSignedOut: () => void;
  setUser: (user: UserResponse) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  status: "restoring",
  user: null,
  setSignedIn: (user) => set({ status: "signedIn", user }),
  setSignedOut: () => set({ status: "signedOut", user: null }),
  setUser: (user) => set({ user }),
}));
