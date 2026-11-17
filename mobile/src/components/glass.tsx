import { GlassView, isLiquidGlassAvailable } from "expo-glass-effect";
import { StyleSheet, useColorScheme, View, type ViewProps } from "react-native";

import { useReduceTransparency } from "@/hooks/use-reduce-transparency";
import { useTheme } from "@/theme/use-theme";

interface GlassProps extends ViewProps {
  tint?: string;
  interactive?: boolean;
}

export function Glass({ tint, interactive = false, style, children, ...rest }: GlassProps) {
  const theme = useTheme();
  const scheme = useColorScheme() === "dark" ? "dark" : "light";
  const reduceTransparency = useReduceTransparency();

  if (isLiquidGlassAvailable() && !reduceTransparency) {
    return (
      <GlassView
        glassEffectStyle="regular"
        tintColor={tint}
        isInteractive={interactive}
        colorScheme={scheme}
        style={style}
        {...rest}
      >
        {children}
      </GlassView>
    );
  }

  return (
    <View
      style={[styles.fallback, { backgroundColor: tint ?? theme.surface, borderColor: theme.border }, style]}
      {...rest}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  fallback: { borderWidth: StyleSheet.hairlineWidth },
});
