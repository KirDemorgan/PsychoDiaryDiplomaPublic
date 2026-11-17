import { useSessionStore } from "@/auth/session-store";
import { useQuickActionStore } from "@/features/quick-actions/quick-action-store";
import { redirectIncomingPath } from "@/lib/native-intent";

export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  const signedIn = useSessionStore.getState().status === "signedIn";
  return redirectIncomingPath(path, signedIn, (action) => useQuickActionStore.getState().request(action));
}
