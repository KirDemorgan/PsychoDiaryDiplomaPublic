import Slider from "@react-native-community/slider";
import { Pressable, StyleSheet, View } from "react-native";

import { RATING_MAX, RATING_MIN } from "@/features/ratings/rating-values";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";

interface RatingSliderProps {
  label: string;
  color: string;
  value: number | null;
  onChange: (value: number | null) => void;
}

export function RatingSlider({ label, color, value, onChange }: RatingSliderProps) {
  const theme = useTheme();
  const valueText = value === null ? "Не указано" : `${value} из ${RATING_MAX}`;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <AppText variant="callout">{label}</AppText>
        <View style={styles.valueRow}>
          <AppText variant="callout" secondary={value === null}>
            {value === null ? "—" : `${value}/${RATING_MAX}`}
          </AppText>
          {value !== null ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Сбросить: ${label}`}
              hitSlop={8}
              onPress={() => onChange(null)}
            >
              <AppText variant="caption" style={{ color: theme.primary }}>
                Сбросить
              </AppText>
            </Pressable>
          ) : null}
        </View>
      </View>
      <Slider
        accessibilityLabel={label}
        accessibilityValue={{ text: valueText }}
        minimumValue={RATING_MIN}
        maximumValue={RATING_MAX}
        step={1}
        value={value ?? RATING_MIN}
        minimumTrackTintColor={value === null ? theme.border : color}
        maximumTrackTintColor={theme.surfaceMuted}
        thumbTintColor={value === null ? theme.textSecondary : color}
        onSlidingComplete={onChange}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.xs },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  valueRow: { flexDirection: "row", alignItems: "center", gap: spacing.md },
});
