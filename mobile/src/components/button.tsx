import { ActivityIndicator, Pressable, StyleSheet, type PressableProps } from "react-native";

import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";
import { Glass } from "./glass";

type ButtonVariant = "primary" | "secondary" | "destructive";

interface ButtonProps extends Omit<PressableProps, "children" | "style"> {
  title: string;
  variant?: ButtonVariant;
  loading?: boolean;
}

export function Button({ title, variant = "primary", loading = false, disabled, ...rest }: ButtonProps) {
  const theme = useTheme();
  const inactive = disabled || loading;
  const foreground = { primary: theme.onPrimary, secondary: theme.primary, destructive: theme.danger }[variant];

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive, busy: loading }}
      disabled={inactive}
      style={({ pressed }) => [styles.pressable, (pressed || inactive) && styles.dimmed]}
      {...rest}
    >
      <Glass tint={variant === "primary" ? theme.primary : undefined} interactive style={styles.glass}>
        {loading ? (
          <ActivityIndicator color={foreground} />
        ) : (
          <AppText variant="headline" style={{ color: foreground }}>
            {title}
          </AppText>
        )}
      </Glass>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pressable: { minHeight: 52 },
  glass: {
    minHeight: 52,
    borderRadius: radius.pill,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.xl,
  },
  dimmed: { opacity: 0.6 },
});
