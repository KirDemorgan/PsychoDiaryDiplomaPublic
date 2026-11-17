import { StyleSheet, View } from "react-native";

import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { GrowBar, Reveal, Wipe } from "../page-motion";

import { MiniChart } from "./mini-chart";

const SLOTS = ["Утро", "День", "Вечер"] as const;
const SELECTED_SLOT = "День";

const ROWS = [
  { label: "Тревожность", value: 4, color: "anxiety" },
  { label: "Настроение", value: 6, color: "mood" },
  { label: "Энергия", value: 5, color: "energy" },
] as const;

const WEEK = [5, 4, 6, 3, 5, 6, 4] as const;

export function RatingsMockup({ width }: { width: number }) {
  const theme = useTheme();
  const chartWidth = width - spacing.lg * 2;

  return (
    <Card style={[styles.card, { width }]}>
      <View style={[styles.segments, { backgroundColor: theme.surfaceMuted }]}>
        {SLOTS.map((slot) => (
          <View key={slot} style={[styles.segment, slot === SELECTED_SLOT && { backgroundColor: theme.primary }]}>
            <AppText
              variant="caption"
              style={{ color: slot === SELECTED_SLOT ? theme.onPrimary : theme.textSecondary, fontWeight: "600" }}
            >
              {slot}
            </AppText>
          </View>
        ))}
      </View>

      {ROWS.map((row, index) => (
        <View key={row.label} style={styles.row}>
          <View style={styles.rowHeader}>
            <AppText variant="callout">{row.label}</AppText>
            <Reveal delay={200 + index * 160} distance={6}>
              <AppText variant="callout" style={{ fontWeight: "600", color: theme[row.color] }}>
                {row.value}/10
              </AppText>
            </Reveal>
          </View>
          <GrowBar value={row.value / 10} color={theme[row.color]} trackColor={theme.surfaceMuted} delay={150 + index * 160} />
        </View>
      ))}

      <View style={styles.chart}>
        <Wipe width={chartWidth} height={96} delay={700}>
          <MiniChart values={WEEK} width={chartWidth} height={96} color={theme.primary} gridColor={theme.border} />
        </Wipe>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.md },
  segments: { flexDirection: "row", borderRadius: radius.pill, padding: 3 },
  segment: { flex: 1, alignItems: "center", paddingVertical: 6, borderRadius: radius.pill },
  row: { gap: 6 },
  rowHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  chart: { alignItems: "flex-start", marginTop: 2 },
});
