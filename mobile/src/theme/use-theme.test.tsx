import { renderHook } from "@testing-library/react-native";
import { useColorScheme } from "react-native";

import { darkPalette, lightPalette } from "./colors";
import { useTheme } from "./use-theme";

jest.mock("react-native/Libraries/Utilities/useColorScheme", () => ({ default: jest.fn() }));

describe("useTheme", () => {
  const scheme = jest.requireMock("react-native/Libraries/Utilities/useColorScheme").default as jest.Mock;

  it("возвращает светлую палитру по умолчанию", async () => {
    scheme.mockReturnValue("light");

    const { result } = await renderHook(() => useTheme());

    expect(result.current).toBe(lightPalette);
  });

  it("возвращает тёмную палитру в тёмной теме", async () => {
    scheme.mockReturnValue("dark");

    const { result } = await renderHook(() => useTheme());

    expect(result.current).toBe(darkPalette);
    expect(useColorScheme).toBeDefined();
  });
});
