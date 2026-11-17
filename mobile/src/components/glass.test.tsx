import { render, screen } from "@testing-library/react-native";
import { AccessibilityInfo, Text } from "react-native";
import * as GlassEffect from "expo-glass-effect";

import { Glass } from "./glass";

const available = GlassEffect.isLiquidGlassAvailable as jest.Mock;

describe("Glass", () => {
  beforeEach(() => {
    available.mockReturnValue(true);
    jest.spyOn(AccessibilityInfo, "isReduceTransparencyEnabled").mockResolvedValue(false);
  });

  it("рисует стекло, когда оно доступно, и передаёт оттенок", async () => {
    await render(
      <Glass tint="#12908C" interactive testID="glass">
        <Text>Внутри</Text>
      </Glass>,
    );

    const view = screen.getByTestId("glass");
    expect(view.props.tintColor).toBe("#12908C");
    expect(view.props.isInteractive).toBe(true);
    expect(view.props.glassEffectStyle).toBe("regular");
    expect(screen.getByText("Внутри")).toBeTruthy();
  });

  it("без поддержки стекла показывает обычную подложку с оттенком", async () => {
    available.mockReturnValue(false);
    await render(
      <Glass tint="#12908C" testID="plain">
        <Text>Текст</Text>
      </Glass>,
    );

    const view = screen.getByTestId("plain");
    expect(view.props.glassEffectStyle).toBeUndefined();
    expect(screen.getByText("Текст")).toBeTruthy();
  });

  it("при включённом «Уменьшить прозрачность» отказывается от стекла", async () => {
    jest.spyOn(AccessibilityInfo, "isReduceTransparencyEnabled").mockResolvedValue(true);
    await render(<Glass testID="plain" />);

    await screen.findByTestId("plain");
    expect(screen.getByTestId("plain").props.glassEffectStyle).toBeUndefined();
  });

  it("рисует подложку без оттенка цветом поверхности", async () => {
    available.mockReturnValue(false);
    await render(<Glass testID="plain" />);

    expect(screen.getByTestId("plain")).toBeTruthy();
  });
});
