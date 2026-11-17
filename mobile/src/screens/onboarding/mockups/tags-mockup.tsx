import { SymbolView } from "expo-symbols";
import { StyleSheet, View } from "react-native";

import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { HighlightedText, type ColoredSpan } from "@/components/highlighted-text";
import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { Pop, Reveal } from "../page-motion";

const TEXT = "Почти не спал. Перед встречей тревожился, а вечером стало спокойнее.";

function spanOf(id: string, fragment: string): { id: string; start: number; end: number } {
  const start = TEXT.indexOf(fragment);
  return { id, start, end: start + fragment.length };
}

const SUGGESTIONS = [
  { id: "sleep", label: "Недостаток сна", quote: "Почти не спал", color: "tagSleep" },
  { id: "anxiety", label: "Тревога", quote: "Перед встречей тревожился", color: "tagAnxiety" },
  { id: "calm", label: "Спокойствие", quote: "вечером стало спокойнее", color: "tagCalm" },
] as const;

export function TagsMockup({ width }: { width: number }) {
  const theme = useTheme();
  const spans: ColoredSpan[] = [
    { ...spanOf("sleep", "Почти не спал."), color: theme.tagSleep },
    { ...spanOf("anxiety", "Перед встречей тревожился"), color: theme.tagAnxiety },
    { ...spanOf("calm", "стало спокойнее"), color: theme.tagCalm },
  ];

  return (
    <Card style={[styles.card, { width }]}>
      <AppText variant="caption" secondary>
        Ваша запись
      </AppText>
      <HighlightedText text={TEXT} spans={spans} />

      <View style={styles.list}>
        {SUGGESTIONS.map((item, index) => (
          <Reveal key={item.id} delay={250 + index * 220} style={[styles.suggestion, { backgroundColor: theme.surfaceMuted }]}>
            <View style={[styles.dot, { backgroundColor: theme[item.color] }]} />
            <View style={styles.suggestionText}>
              <AppText variant="callout" style={{ fontWeight: "600" }}>
                {item.label}
              </AppText>
              <AppText variant="caption" secondary numberOfLines={1}>
                «{item.quote}»
              </AppText>
            </View>
            <Pop delay={650 + index * 220} style={[styles.check, { backgroundColor: theme.primary }]}>
              <SymbolView name="checkmark" size={13} tintColor={theme.onPrimary} weight="bold" />
            </Pop>
          </Reveal>
        ))}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.sm },
  list: { gap: spacing.sm, marginTop: spacing.xs },
  suggestion: { flexDirection: "row", alignItems: "center", gap: spacing.md, padding: spacing.md, borderRadius: radius.field },
  dot: { width: 12, height: 12, borderRadius: 6 },
  suggestionText: { flex: 1 },
  check: { width: 24, height: 24, borderRadius: 12, alignItems: "center", justifyContent: "center" },
});
