import { SymbolView } from "expo-symbols";
import { StyleSheet, View } from "react-native";
import Animated, { useAnimatedStyle } from "react-native-reanimated";

import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { Glass } from "@/components/glass";
import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { Reveal, useEntrance, usePop } from "../page-motion";

function IntakeStatus() {
  const theme = useTheme();
  const done = useEntrance(1700, 420);
  const pending = useAnimatedStyle(() => ({ opacity: 1 - done.get() }));
  const taken = useAnimatedStyle(() => ({ opacity: done.get() }));

  return (
    <View style={styles.statusBox}>
      <Animated.View style={[styles.statusLayer, pending]}>
        <AppText variant="caption" secondary>
          Нет отметки
        </AppText>
      </Animated.View>
      <Animated.View style={[styles.statusLayer, styles.statusTaken, taken]}>
        <SymbolView name="checkmark.circle.fill" size={14} tintColor={theme.mood} />
        <AppText variant="caption" style={{ color: theme.mood, fontWeight: "600" }}>
          Принято
        </AppText>
      </Animated.View>
    </View>
  );
}

function TakeButton() {
  const theme = useTheme();
  const press = usePop(1500);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.06 * Math.sin(Math.PI * Math.min(1, press.get())) }] }));

  return (
    <Animated.View style={[styles.take, { backgroundColor: theme.primary }, style]}>
      <AppText variant="callout" style={{ color: theme.onPrimary, fontWeight: "600" }}>
        Принял
      </AppText>
    </Animated.View>
  );
}

export function MedicationsMockup({ width }: { width: number }) {
  const theme = useTheme();

  return (
    <View style={{ width, gap: spacing.md }}>
      <Reveal distance={-18}>
        <Glass style={styles.banner}>
          <View style={[styles.bell, { backgroundColor: theme.primary }]}>
            <SymbolView name="bell.fill" size={15} tintColor={theme.onPrimary} />
          </View>
          <View style={styles.bannerText}>
            <AppText variant="caption" secondary>
              Напоминание · 09:00
            </AppText>
            <AppText variant="callout" style={{ fontWeight: "600" }}>
              Пора принять препарат
            </AppText>
          </View>
        </Glass>
      </Reveal>

      <Card style={styles.card}>
        <AppText variant="headline">Приёмы сегодня</AppText>
        <View style={styles.dose}>
          <View style={styles.doseText}>
            <AppText variant="callout" style={{ fontWeight: "600" }}>
              09:00 · Препарат А
            </AppText>
            <AppText variant="caption" secondary>
              1 таб.
            </AppText>
          </View>
          <IntakeStatus />
        </View>
        <View style={styles.actions}>
          <TakeButton />
          <View style={[styles.skip, { borderColor: theme.border }]}>
            <AppText variant="callout" style={{ color: theme.primary, fontWeight: "600" }}>
              Пропустил
            </AppText>
          </View>
        </View>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: { flexDirection: "row", alignItems: "center", gap: spacing.md, padding: spacing.md, borderRadius: radius.card },
  bell: { width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  bannerText: { flex: 1 },
  card: { gap: spacing.md },
  dose: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  doseText: { gap: 2 },
  statusBox: { minWidth: 96, height: 20, alignItems: "flex-end", justifyContent: "center" },
  statusLayer: { position: "absolute", right: 0 },
  statusTaken: { flexDirection: "row", alignItems: "center", gap: 4 },
  actions: { flexDirection: "row", gap: spacing.sm },
  take: { flex: 1, minHeight: 44, borderRadius: radius.pill, alignItems: "center", justifyContent: "center" },
  skip: {
    flex: 1,
    minHeight: 44,
    borderRadius: radius.pill,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: StyleSheet.hairlineWidth,
  },
});
