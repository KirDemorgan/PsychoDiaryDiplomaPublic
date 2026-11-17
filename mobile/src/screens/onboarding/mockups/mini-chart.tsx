import Svg, { Circle, Defs, LinearGradient, Path, Stop } from "react-native-svg";

import { areaPath, chartPoints, linePath } from "./chart-path";

interface MiniChartProps {
  values: readonly number[];
  width: number;
  height: number;
  color: string;
  gridColor: string;
}

const PADDING = 10;

export function MiniChart({ values, width, height, color, gridColor }: MiniChartProps) {
  const points = chartPoints(values, width, height, PADDING);
  const last = points[points.length - 1];

  return (
    <Svg width={width} height={height} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Defs>
        <LinearGradient id="miniChartFill" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={color} stopOpacity={0.32} />
          <Stop offset="1" stopColor={color} stopOpacity={0} />
        </LinearGradient>
      </Defs>
      {[0.25, 0.55, 0.85].map((fraction) => (
        <Path
          key={fraction}
          d={`M${PADDING} ${height * fraction} L${width - PADDING} ${height * fraction}`}
          stroke={gridColor}
          strokeWidth={1}
          strokeDasharray="3 5"
        />
      ))}
      <Path d={areaPath(points, height - PADDING)} fill="url(#miniChartFill)" />
      <Path d={linePath(points)} stroke={color} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      {last ? <Circle cx={last.x} cy={last.y} r={5} fill={color} /> : null}
    </Svg>
  );
}
