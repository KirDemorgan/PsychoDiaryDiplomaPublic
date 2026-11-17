import { Text, useWindowDimensions, type TextProps } from "react-native";

import { typography, type TypographyVariant } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export const MAX_FONT_SCALE = 1.5;

interface AppTextProps extends TextProps {
  variant?: TypographyVariant;
  secondary?: boolean;
}

export function AppText({ variant = "body", secondary = false, style, ...rest }: AppTextProps) {
  const theme = useTheme();
  const { fontScale } = useWindowDimensions();
  const base = typography[variant];
  const lineHeight = Math.round(base.lineHeight * Math.min(Math.max(fontScale, 1), MAX_FONT_SCALE));

  return (
    <Text
      maxFontSizeMultiplier={MAX_FONT_SCALE}
      style={[base, { lineHeight, color: secondary ? theme.textSecondary : theme.textPrimary }, style]}
      {...rest}
    />
  );
}
