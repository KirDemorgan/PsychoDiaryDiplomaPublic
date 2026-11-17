import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { AccessibilityInfo, Dimensions } from "react-native";

import { useSessionStore } from "@/auth/session-store";
import { ONBOARDING_PAGES } from "@/features/onboarding/pages";
import { useOnboardingStore } from "@/features/onboarding/onboarding-store";
import { router } from "@/test/mocks";

import { OnboardingScreen } from "./onboarding-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-symbols", () => jest.requireActual("@/test/mocks").symbolsMock);
jest.mock("react-native-safe-area-context", () => jest.requireActual("react-native-safe-area-context/jest/mock").default);
jest.mock("@react-native-async-storage/async-storage", () =>
  jest.requireActual("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

const settle = async (pageIndex: number) => {
  const pager = screen.getByTestId("onboarding-pager");
  await fireEvent(pager, "momentumScrollEnd", { nativeEvent: { contentOffset: { x: pageIndex * Dimensions.get("window").width } } });
};

describe("OnboardingScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(AccessibilityInfo, "isReduceMotionEnabled").mockResolvedValue(false);
    jest.spyOn(AccessibilityInfo, "announceForAccessibility").mockImplementation(() => undefined);
    jest.spyOn(AccessibilityInfo, "addEventListener").mockReturnValue({ remove: jest.fn() } as never);
    useSessionStore.setState({ status: "signedOut", user: null });
    useOnboardingStore.setState({ status: "pending" });
  });

  it("показывает все четыре темы и обещанные тексты", async () => {
    await render(<OnboardingScreen />);

    for (const page of ONBOARDING_PAGES) {
      expect(screen.getAllByText(page.title, { includeHiddenElements: true }).length).toBeGreaterThan(0);
      expect(screen.getAllByText(page.body, { includeHiddenElements: true })).toHaveLength(1);
    }
    expect(ONBOARDING_PAGES).toHaveLength(4);
  });

  it("помечает страницы для VoiceOver и скрывает неактивные", async () => {
    await render(<OnboardingScreen />);

    expect(screen.getByLabelText("Страница 1 из 4")).toBeTruthy();
    expect(screen.getByLabelText("Страница 2 из 4", { includeHiddenElements: true }).props.accessibilityElementsHidden).toBe(true);
    expect(screen.getByRole("progressbar").props.accessibilityValue).toMatchObject({ now: 1, max: 4 });
  });

  it("листает кнопкой «Далее» и меняет подпись на последней странице", async () => {
    await render(<OnboardingScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Далее" }));
    expect(screen.getByRole("progressbar").props.accessibilityValue).toMatchObject({ now: 2 });
    await fireEvent.press(screen.getByRole("button", { name: "Далее" }));
    await fireEvent.press(screen.getByRole("button", { name: "Далее" }));

    expect(screen.getByRole("button", { name: "Начать" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Пропустить знакомство" })).toBeNull();
  });

  it("следует за свайпом и объявляет страницу", async () => {
    await render(<OnboardingScreen />);

    await settle(2);

    expect(screen.getByRole("progressbar").props.accessibilityValue).toMatchObject({ now: 3 });
    expect(AccessibilityInfo.announceForAccessibility).toHaveBeenCalledWith(expect.stringContaining("Страница 3 из 4"));
  });

  it("первый запуск: «Начать» запоминает прохождение и ведёт ко входу", async () => {
    await render(<OnboardingScreen />);
    await settle(3);

    await fireEvent.press(screen.getByRole("button", { name: "Начать" }));

    await waitFor(() => expect(useOnboardingStore.getState().status).toBe("completed"));
    expect(router.replace).toHaveBeenCalledWith("/sign-in");
    expect(router.back).not.toHaveBeenCalled();
  });

  it("«Пропустить» засчитывает знакомство пройденным", async () => {
    await render(<OnboardingScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Пропустить знакомство" }));

    await waitFor(() => expect(useOnboardingStore.getState().status).toBe("completed"));
    expect(router.replace).toHaveBeenCalledWith("/sign-in");
  });

  it("повтор из настроек: возвращает назад, а не на экран входа", async () => {
    useSessionStore.setState({
      status: "signedIn",
      user: { id: "u1", email: "a@b.co", displayName: null, timeZone: "UTC", version: 0, dataRevision: 0 },
    });
    await render(<OnboardingScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Пропустить знакомство" }));

    expect(router.back).toHaveBeenCalledTimes(1);
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("работает при включённом «Уменьшить движение»", async () => {
    jest.spyOn(AccessibilityInfo, "isReduceMotionEnabled").mockResolvedValue(true);
    await render(<OnboardingScreen />);

    await act(async () => undefined);

    expect(screen.getByText(ONBOARDING_PAGES[0].title)).toBeTruthy();
  });
});
