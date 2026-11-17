import { SymbolView } from "expo-symbols";
import { Pressable, StyleSheet, View } from "react-native";

import type { Candidate } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { PRESENCE_LABELS, TEMPORAL_LABELS } from "@/features/tags/labels";
import type { CandidateDecision } from "@/features/tags/review-state";
import { TAG_LABELS, tagColor } from "@/features/tags/tag-colors";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

interface CandidateCardProps {
  candidate: Candidate;
  decision: CandidateDecision;
  onToggle: () => void;
  onEdit: () => void;
}

export function CandidateCard({ candidate, decision, onToggle, onEdit }: CandidateCardProps) {
  const theme = useTheme();
  const accepted = decision.action !== "REJECT";
  const shown = decision.action === "EDIT" ? decision.replacement : candidate;
  const label = TAG_LABELS[shown.labelCode];

  return (
    <Card style={styles.card}>
      <Pressable
        accessibilityRole="checkbox"
        accessibilityLabel={label}
        accessibilityState={{ checked: accepted }}
        onPress={onToggle}
        style={styles.check}
        hitSlop={8}
      >
        <SymbolView
          name={accepted ? "checkmark.square.fill" : "square"}
          tintColor={accepted ? theme.primary : theme.textSecondary}
          size={26}
        />
      </Pressable>

      <View style={styles.body}>
        <View style={styles.title}>
          <View style={[styles.dot, { backgroundColor: tagColor(shown.labelCode, theme) }]} />
          <AppText variant="headline">{label}</AppText>
        </View>
        <AppText variant="caption" secondary numberOfLines={2}>
          «{candidate.quote}»
        </AppText>
        <AppText variant="caption" secondary>
          {PRESENCE_LABELS[shown.presence]} · {TEMPORAL_LABELS[shown.temporal]}
          {decision.action === "EDIT" ? " · изменено" : ""}
        </AppText>
      </View>

      <Pressable accessibilityRole="button" accessibilityLabel={`Изменить: ${label}`} onPress={onEdit} hitSlop={8}>
        <SymbolView name="pencil" tintColor={theme.textSecondary} size={20} />
      </Pressable>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { flexDirection: "row", alignItems: "center", gap: spacing.md },
  check: { alignSelf: "center" },
  body: { flex: 1, gap: 2 },
  title: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  dot: { width: 12, height: 12, borderRadius: 6 },
});
