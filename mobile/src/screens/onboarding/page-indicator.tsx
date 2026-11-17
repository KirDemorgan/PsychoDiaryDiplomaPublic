import { StyleSheet, View } from "react-native";
import Animated, { Extrapolation, interpolate, useAnimatedStyle, type SharedValue } from "react-native-reanimated";

import { useTheme } from "@/theme/use-theme";

const DOT_SIZE = 8;
const DOT_ACTIVE_WIDTH = 26;

interface PageIndicatorProps {
  count: number;
  page: number;
  width: number;
  scrollX: SharedValue<number>;
}

function Dot({ index, width, scrollX, color }: { index: number; width: number; scrollX: SharedValue<number> } & { color: string }) {
  const style = useAnimatedStyle(() => {
    const position = scrollX.get() / width;
    const range = [index - 1, index, index + 1];
    return {
      width: interpolate(position, range, [DOT_SIZE, DOT_ACTIVE_WIDTH, DOT_SIZE], Extrapolation.CLAMP),
      opacity: interpolate(position, range, [0.28, 1, 0.28], Extrapolation.CLAMP),
    };
  });

  return <Animated.View style={[styles.dot, { backgroundColor: color }, style]} />;
}

export function PageIndicator({ count, page, width, scrollX }: PageIndicatorProps) {
  const theme = useTheme();

  return (
    <View
      style={styles.row}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel="Знакомство с приложением"
      accessibilityValue={{ min: 1, max: count, now: page + 1, text: `Страница ${page + 1} из ${count}` }}
    >
      {Array.from({ length: count }, (_, index) => (
        <Dot key={index} index={index} width={width} scrollX={scrollX} color={theme.primary} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, height: 12 },
  dot: { height: DOT_SIZE, borderRadius: DOT_SIZE / 2 },
});
