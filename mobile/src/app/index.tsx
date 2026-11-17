import { Redirect } from "expo-router";

import { useSessionStore } from "@/auth/session-store";
import { resolveInitialRoute } from "@/features/onboarding/initial-route";
import { useOnboardingStore } from "@/features/onboarding/onboarding-store";

export default function Index() {
  const signedIn = useSessionStore((state) => state.status === "signedIn");
  const onboarding = useOnboardingStore((state) => state.status);

  return <Redirect href={resolveInitialRoute(signedIn, onboarding)} />;
}
