import AsyncStorage from "@react-native-async-storage/async-storage";

import { useOnboardingStore } from "./onboarding-store";
import { ONBOARDING_KEY, loadOnboardingCompleted, saveOnboardingCompleted } from "./onboarding-storage";

jest.mock("@react-native-async-storage/async-storage", () =>
  jest.requireActual("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

describe("onboarding storage", () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    useOnboardingStore.setState({ status: "loading" });
  });

  it("считает знакомство непройденным при первом запуске", async () => {
    await useOnboardingStore.getState().restore();

    expect(useOnboardingStore.getState().status).toBe("pending");
  });

  it("запоминает прохождение и читает его при следующем запуске", async () => {
    await useOnboardingStore.getState().complete();
    useOnboardingStore.setState({ status: "loading" });

    await useOnboardingStore.getState().restore();

    expect(useOnboardingStore.getState().status).toBe("completed");
    expect(await AsyncStorage.getItem(ONBOARDING_KEY)).toBe("1");
  });

  it("не ломается, если хранилище недоступно", async () => {
    jest.spyOn(AsyncStorage, "getItem").mockRejectedValueOnce(new Error("недоступно"));
    jest.spyOn(AsyncStorage, "setItem").mockRejectedValueOnce(new Error("недоступно"));

    await expect(loadOnboardingCompleted()).resolves.toBe(false);
    await expect(saveOnboardingCompleted()).resolves.toBeUndefined();
  });
});
