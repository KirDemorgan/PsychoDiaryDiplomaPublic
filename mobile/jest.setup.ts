jest.mock("expo-glass-effect", () => {
  const { createElement } = jest.requireActual("react");
  const { View } = jest.requireActual("react-native");
  return {
    GlassView: (props: Record<string, unknown>) => createElement(View, props),
    GlassContainer: (props: Record<string, unknown>) => createElement(View, props),
    isLiquidGlassAvailable: jest.fn(() => true),
    isGlassEffectAPIAvailable: jest.fn(() => true),
  };
});
