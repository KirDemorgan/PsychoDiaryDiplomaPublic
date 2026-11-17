import { StyleSheet, View, type ViewProps } from "react-native";

import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function Card({ style, ...rest }: ViewProps) {
  const theme = useTheme();
  return <View style={[styles.card, { backgroundColor: theme.surface }, style]} {...rest} />;
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.card,
    padding: spacing.lg,
    gap: spacing.md,
    borderCurve: "continuous",
  },
});
