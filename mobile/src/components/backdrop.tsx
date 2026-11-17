import { StyleSheet, View } from "react-native";
import Svg, { Circle, Defs, RadialGradient, Stop } from "react-native-svg";

import { useTheme } from "@/theme/use-theme";

const ORBS = [
  { id: "teal", cx: "88%", cy: "6%", r: "46%", color: "primary", opacity: 0.22 },
  { id: "violet", cx: "6%", cy: "38%", r: "42%", color: "anxiety", opacity: 0.16 },
  { id: "amber", cx: "92%", cy: "78%", r: "44%", color: "tagAnxiety", opacity: 0.14 },
] as const;

export function Backdrop() {
  const theme = useTheme();
  return (
    <View pointerEvents="none" style={styles.fill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Svg width="100%" height="100%">
        <Defs>
          {ORBS.map((orb) => (
            <RadialGradient key={orb.id} id={orb.id} cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor={theme[orb.color]} stopOpacity={orb.opacity} />
              <Stop offset="1" stopColor={theme[orb.color]} stopOpacity={0} />
            </RadialGradient>
          ))}
        </Defs>
        {ORBS.map((orb) => (
          <Circle key={orb.id} cx={orb.cx} cy={orb.cy} r={orb.r} fill={`url(#${orb.id})`} />
        ))}
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { position: "absolute", left: 0, right: 0, top: -400, bottom: -400 },
});
