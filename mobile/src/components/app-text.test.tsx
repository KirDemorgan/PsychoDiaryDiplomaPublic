import { render, screen } from "@testing-library/react-native";
import { StyleSheet } from "react-native";

import { AppText, MAX_FONT_SCALE } from "./app-text";

const mockDimensions = jest.fn();
jest.mock("react-native/Libraries/Utilities/useWindowDimensions", () => ({
  __esModule: true,
  default: () => mockDimensions(),
}));

const withFontScale = (fontScale: number) => mockDimensions.mockReturnValue({ width: 390, height: 844, scale: 3, fontScale });
const lineHeightOf = (text: string) => StyleSheet.flatten(screen.getByText(text).props.style).lineHeight;

describe("AppText", () => {
  it("масштабирует высоту строки вместе с размером шрифта iOS", async () => {
    withFontScale(1.3);
    await render(<AppText variant="body">Текст</AppText>);

    expect(lineHeightOf("Текст")).toBe(Math.round(22 * 1.3));
  });

  it("не уменьшает строку при шрифте меньше обычного", async () => {
    withFontScale(0.8);
    await render(<AppText variant="body">Текст</AppText>);

    expect(lineHeightOf("Текст")).toBe(22);
  });

  it("ограничивает масштаб, чтобы вёрстка не ломалась", async () => {
    withFontScale(3);
    await render(<AppText variant="title">Заголовок</AppText>);

    expect(lineHeightOf("Заголовок")).toBe(Math.round(28 * MAX_FONT_SCALE));
    expect(screen.getByText("Заголовок").props.maxFontSizeMultiplier).toBe(MAX_FONT_SCALE);
  });

  it("использует вторичный цвет", async () => {
    withFontScale(1);
    await render(<AppText secondary>Подпись</AppText>);

    expect(StyleSheet.flatten(screen.getByText("Подпись").props.style).color).toBeTruthy();
  });
});
