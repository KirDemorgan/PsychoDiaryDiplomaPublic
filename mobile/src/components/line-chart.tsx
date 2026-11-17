import { useState } from "react";
import { type LayoutChangeEvent, StyleSheet, View } from "react-native";
import Svg, { Circle, Defs, G, Line, LinearGradient, Path, Stop, Text as SvgText } from "react-native-svg";

import { buildChart } from "@/features/analytics/chart-geometry";
import { useTheme } from "@/theme/use-theme";

const HEIGHT = 220;
const PADDING = { top: 12, right: 24, bottom: 28, left: 28 };
const TICKS = [0, 2, 4, 6, 8, 10];

interface LineChartProps {
  values: readonly (number | null)[];
  labels: readonly string[];
  color: string;
  threshold?: number;
  accessibilityLabel: string;
}

export function LineChart({ values, labels, color, threshold, accessibilityLabel }: LineChartProps) {
  const theme = useTheme();
  const [width, setWidth] = useState(0);
  const chart = buildChart(values, { width, height: HEIGHT, padding: PADDING });
  const gradientId = `fill-${color.replace("#", "")}`;
  const labelStep = Math.max(1, Math.ceil(values.length / 6));

  const onLayout = (event: LayoutChangeEvent) => setWidth(Math.round(event.nativeEvent.layout.width));

  return (
    <View onLayout={onLayout} accessible accessibilityRole="image" accessibilityLabel={accessibilityLabel} style={styles.container}>
      {width > 0 ? (
        <Svg width={width} height={HEIGHT}>
          <Defs>
            <LinearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <Stop offset="0" stopColor={color} stopOpacity={0.35} />
              <Stop offset="1" stopColor={color} stopOpacity={0.02} />
            </LinearGradient>
          </Defs>

          {TICKS.map((tick) => (
            <G key={tick}>
              <Line
                x1={chart.plot.left}
                x2={chart.plot.right}
                y1={chart.yFor(tick)}
                y2={chart.yFor(tick)}
                stroke={theme.border}
                strokeWidth={StyleSheet.hairlineWidth}
              />
              <SvgText x={chart.plot.left - 8} y={chart.yFor(tick) + 4} fontSize={11} fill={theme.textSecondary} textAnchor="end">
                {tick}
              </SvgText>
            </G>
          ))}

          {threshold !== undefined ? (
            <Line
              x1={chart.plot.left}
              x2={chart.plot.right}
              y1={chart.yFor(threshold)}
              y2={chart.yFor(threshold)}
              stroke={theme.danger}
              strokeWidth={1}
              strokeDasharray="4 4"
            />
          ) : null}

          {chart.segments.map((segment) => (
            <G key={segment.points[0].index}>
              {segment.area ? <Path d={segment.area} fill={`url(#${gradientId})`} /> : null}
              {segment.line ? (
                <Path d={segment.line} stroke={color} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" fill="none" />
              ) : null}
              {segment.points.map((point) => (
                <Circle key={point.index} cx={point.x} cy={point.y} r={4} fill={color} />
              ))}
            </G>
          ))}

          {labels.map((label, index) =>
            index % labelStep === 0 ? (
              <SvgText key={label} x={chart.xFor(index)} y={HEIGHT - 8} fontSize={11} fill={theme.textSecondary} textAnchor="middle">
                {label}
              </SvgText>
            ) : null,
          )}
        </Svg>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { height: HEIGHT, width: "100%" },
});
