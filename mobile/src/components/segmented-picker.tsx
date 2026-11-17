import { Pressable, StyleSheet, View } from "react-native";

import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";
import { Glass } from "./glass";

interface SegmentedPickerProps<T extends string> {
  options: readonly { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  accessibilityLabel: string;
}

export function SegmentedPicker<T extends string>({
  options,
  value,
  onChange,
  accessibilityLabel,
}: SegmentedPickerProps<T>) {
  const theme = useTheme();
  return (
    <Glass style={styles.track}>
      <View accessibilityRole="radiogroup" accessibilityLabel={accessibilityLabel} style={styles.row}>
        {options.map((option) => {
          const selected = option.value === value;
          return (
            <Pressable
              key={option.value}
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              onPress={() => onChange(option.value)}
              style={styles.segment}
            >
              {selected ? <Glass tint={theme.primary} interactive style={styles.selected} /> : null}
              <AppText
                variant="callout"
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.8}
                style={{ color: selected ? theme.onPrimary : theme.textPrimary, fontWeight: selected ? "600" : "400" }}
              >
                {option.label}
              </AppText>
            </Pressable>
          );
        })}
      </View>
    </Glass>
  );
}

const styles = StyleSheet.create({
  track: { borderRadius: radius.pill, padding: 3 },
  row: { flexDirection: "row" },
  segment: {
    flex: 1,
    minHeight: 38,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    paddingHorizontal: spacing.xs,
  },
  selected: { ...StyleSheet.absoluteFill, borderRadius: radius.pill },
});
