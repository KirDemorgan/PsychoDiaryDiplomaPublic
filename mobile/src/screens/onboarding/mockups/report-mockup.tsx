import { SymbolView } from "expo-symbols";
import { StyleSheet, View } from "react-native";

import { AppText } from "@/components/app-text";
import { Glass } from "@/components/glass";
import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { Pop, Reveal, Wipe } from "../page-motion";

import { MiniChart } from "./mini-chart";

const PAPER = "#FFFFFF";
const PAPER_TEXT = "#111B26";
const PAPER_MUTED = "#5B6B7C";
const PAPER_LINE = "#E3E9F0";

const WEEK = [6, 5, 7, 4, 5, 3, 4] as const;
const LINES = [0.92, 0.78, 0.86] as const;

export function ReportMockup({ width }: { width: number }) {
  const theme = useTheme();
  const sheetWidth = width - spacing.xl;
  const chartWidth = sheetWidth - spacing.lg * 2;

  return (
    <View style={{ width, alignItems: "center" }}>
      <View style={[styles.sheet, { width: sheetWidth, backgroundColor: PAPER }]}>
        <View style={styles.header}>
          <View>
            <AppText variant="headline" style={{ color: PAPER_TEXT }}>
              Отчёт для врача
            </AppText>
            <AppText variant="caption" style={{ color: PAPER_MUTED }}>
              13–19 октября
            </AppText>
          </View>
          <View style={[styles.badge, { backgroundColor: theme.primary }]}>
            <AppText variant="caption" style={{ color: theme.onPrimary, fontWeight: "700" }}>
              PDF
            </AppText>
          </View>
        </View>

        <Wipe width={chartWidth} height={96} delay={300}>
          <MiniChart values={WEEK} width={chartWidth} height={96} color={theme.anxiety} gridColor={PAPER_LINE} />
        </Wipe>

        <View style={styles.lines}>
          {LINES.map((fraction, index) => (
            <Reveal key={fraction} delay={700 + index * 150} distance={8}>
              <View style={[styles.line, { width: `${fraction * 100}%`, backgroundColor: PAPER_LINE }]} />
            </Reveal>
          ))}
        </View>

        <AppText variant="caption" style={{ color: PAPER_MUTED }}>
          Материал для обсуждения, не медицинское заключение.
        </AppText>
      </View>

      <Pop delay={1100} style={styles.share}>
        <Glass interactive style={styles.shareGlass}>
          <SymbolView name="square.and.arrow.up" size={16} tintColor={theme.primary} />
          <AppText variant="callout" style={{ color: theme.primary, fontWeight: "600" }}>
            Поделиться
          </AppText>
        </Glass>
      </Pop>
    </View>
  );
}

const styles = StyleSheet.create({
  sheet: {
    borderRadius: 18,
    borderCurve: "continuous",
    padding: spacing.lg,
    gap: spacing.md,
    transform: [{ rotate: "-1.5deg" }],
    shadowColor: "#0A2540",
    shadowOpacity: 0.18,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 10 },
  },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: radius.pill },
  lines: { gap: spacing.sm },
  line: { height: 8, borderRadius: 4 },
  share: { marginTop: -spacing.lg, alignSelf: "flex-end", marginRight: spacing.sm },
  shareGlass: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: spacing.lg, paddingVertical: 10, borderRadius: radius.pill },
});
