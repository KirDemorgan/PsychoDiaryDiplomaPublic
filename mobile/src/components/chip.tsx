import { Pressable, StyleSheet } from "react-native";

import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";
import { Glass } from "./glass";

interface ChipProps {
  label: string;
  selected: boolean;
  color?: string;
  onPress: () => void;
}

export function Chip({ label, selected, color, onPress }: ChipProps) {
  const theme = useTheme();
  const accent = color ?? theme.primary;
  return (
    <Pressable accessibilityRole="radio" accessibilityState={{ selected }} onPress={onPress}>
      <Glass tint={selected ? `${accent}66` : undefined} interactive style={styles.chip}>
        <AppText variant="callout" style={{ fontWeight: selected ? "600" : "400" }}>
          {label}
        </AppText>
      </Glass>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: 38,
    justifyContent: "center",
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
  },
});
