import AsyncStorage from "@react-native-async-storage/async-storage";

export const ONBOARDING_KEY = "diary.onboarding.completed";

export async function loadOnboardingCompleted(): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(ONBOARDING_KEY)) === "1";
  } catch {
    return false;
  }
}

export async function saveOnboardingCompleted(): Promise<void> {
  try {
    await AsyncStorage.setItem(ONBOARDING_KEY, "1");
  } catch {
    return;
  }
}
