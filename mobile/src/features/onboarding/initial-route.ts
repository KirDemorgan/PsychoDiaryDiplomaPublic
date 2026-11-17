import type { OnboardingStatus } from "./onboarding-store";

export type InitialRoute = "/today" | "/onboarding" | "/sign-in";

export function resolveInitialRoute(signedIn: boolean, onboarding: OnboardingStatus): InitialRoute {
  if (signedIn) {
    return "/today";
  }
  return onboarding === "pending" ? "/onboarding" : "/sign-in";
}
