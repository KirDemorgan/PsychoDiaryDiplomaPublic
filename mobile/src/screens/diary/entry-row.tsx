import { Link } from "expo-router";
import { Pressable, StyleSheet } from "react-native";

import type { EntrySummary } from "@/api/entries-types";
import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { tagLabel } from "@/features/tags/tag-colors";
import { formatEntryMoment } from "@/lib/format";
import { spacing } from "@/theme/tokens";

export function EntryRow({ entry }: { entry: EntrySummary }) {
  return (
    <Link href={{ pathname: "/entry/[id]", params: { id: entry.id } }} asChild>
      <Pressable accessibilityRole="link" style={styles.pressable}>
        <Card style={styles.card}>
          <AppText variant="caption" secondary>
            {formatEntryMoment(entry.occurredAt, entry.timeZone)}
          </AppText>
          <AppText numberOfLines={3}>{entry.textPreview}</AppText>
          {entry.confirmedTags.length > 0 ? (
            <AppText variant="caption" secondary>
              {entry.confirmedTags.map(tagLabel).join(" · ")}
            </AppText>
          ) : null}
        </Card>
      </Pressable>
    </Link>
  );
}

const styles = StyleSheet.create({
  pressable: { marginBottom: spacing.md },
  card: { gap: spacing.xs },
});
