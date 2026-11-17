import AsyncStorage from "@react-native-async-storage/async-storage";
import { renderHook, waitFor } from "@testing-library/react-native";

import { resolveInitialRoute } from "./initial-route";
import { useOnboardingStore } from "./onboarding-store";
import { useOnboardingBootstrap } from "./use-onboarding-bootstrap";

jest.mock("@react-native-async-storage/async-storage", () =>
  jest.requireActual("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

describe("resolveInitialRoute", () => {
  it("авторизованного ведёт на «Сегодня», игнорируя знакомство", () => {
    expect(resolveInitialRoute(true, "pending")).toBe("/today");
    expect(resolveInitialRoute(true, "completed")).toBe("/today");
  });

  it("при первом запуске без входа показывает знакомство", () => {
    expect(resolveInitialRoute(false, "pending")).toBe("/onboarding");
  });

  it("после знакомства ведёт ко входу", () => {
    expect(resolveInitialRoute(false, "completed")).toBe("/sign-in");
  });
});

describe("useOnboardingBootstrap", () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    useOnboardingStore.setState({ status: "loading" });
  });

  it("читает сохранённый флаг при старте", async () => {
    const { result } = await renderHook(() => useOnboardingBootstrap("signedOut"));

    await waitFor(() => expect(result.current).toBe("pending"));
  });

  it("у уже вошедшего пользователя засчитывает знакомство пройденным", async () => {
    const { result } = await renderHook(() => useOnboardingBootstrap("signedIn"));

    await waitFor(() => expect(result.current).toBe("completed"));
    expect(await AsyncStorage.getItem("diary.onboarding.completed")).toBe("1");
  });

  it("пока сессия восстанавливается, ничего не отмечает", async () => {
    const { result } = await renderHook(() => useOnboardingBootstrap("restoring"));

    await waitFor(() => expect(result.current).toBe("pending"));
    expect(await AsyncStorage.getItem("diary.onboarding.completed")).toBeNull();
  });
});
