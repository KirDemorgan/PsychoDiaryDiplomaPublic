import { Link } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { formatDose, formatPlannedTime } from "@/features/medications/format";
import { STATUS_LABELS } from "@/features/medications/labels";
import { METRIC_LABELS } from "@/features/analytics/metrics";
import { SLOT_LABELS } from "@/features/ratings/slot-labels";
import { RATING_SLOTS } from "@/api/ratings-types";
import { TAG_LABELS } from "@/features/tags/tag-colors";
import { useDay } from "@/hooks/use-calendar";
import { formatDateOnly, formatEntryMoment } from "@/lib/format";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const valueText = (value: number | null | undefined) => (value === null || value === undefined ? "—" : String(value));

export function DayScreen({ date }: { date: string }) {
  const theme = useTheme();
  const [timeZone] = useState(deviceTimeZone);
  const day = useDay(date, timeZone);

  if (day.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка дня" />
      </Screen>
    );
  }
  if (day.isError) {
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {describeError(day.error)}
        </AppText>
        <Button title="Повторить" variant="secondary" onPress={() => day.refetch()} />
      </Screen>
    );
  }

  const { day: summary, entries, occurrences } = day.data;
  const ratedSlots = RATING_SLOTS.filter((slot) => summary.ratings[slot]);

  return (
    <Screen>
      <AppText variant="title">{formatDateOnly(date)}</AppText>

      <Card>
        <AppText variant="headline">Оценки</AppText>
        {ratedSlots.length === 0 ? <AppText secondary>Оценок за этот день нет.</AppText> : null}
        {ratedSlots.map((slot) => {
          const rating = summary.ratings[slot]!;
          return (
            <AppText key={slot} variant="callout">
              {SLOT_LABELS[slot]}: {METRIC_LABELS.ANXIETY.toLowerCase()} {valueText(rating.anxiety)}, настроение{" "}
              {valueText(rating.mood)}, энергия {valueText(rating.energy)}
            </AppText>
          );
        })}
      </Card>

      <Card>
        <AppText variant="headline">Записи</AppText>
        {entries.items.length === 0 ? <AppText secondary>Записей за этот день нет.</AppText> : null}
        {entries.items.map((entry) => (
          <Link key={entry.id} href={{ pathname: "/entry/[id]", params: { id: entry.id } }} asChild>
            <Pressable accessibilityRole="link" style={styles.entry}>
              <AppText variant="caption" secondary>
                {formatEntryMoment(entry.occurredAt, entry.timeZone)}
              </AppText>
              <AppText numberOfLines={3}>{entry.textPreview}</AppText>
            </Pressable>
          </Link>
        ))}
        {summary.pendingReviewCount > 0 ? (
          <AppText variant="caption" secondary>
            Ждут проверки тегов: {summary.pendingReviewCount}.
          </AppText>
        ) : null}
      </Card>

      {summary.confirmedPresentTagCodes.length > 0 ? (
        <Card>
          <AppText variant="headline">Подтверждённые теги</AppText>
          <AppText>{summary.confirmedPresentTagCodes.map((code) => TAG_LABELS[code]).join(", ")}</AppText>
          {summary.ambiguousTagCodes.length > 0 ? (
            <AppText variant="caption" secondary>
              Противоречия: {summary.ambiguousTagCodes.map((code) => TAG_LABELS[code]).join(", ")}.
            </AppText>
          ) : null}
        </Card>
      ) : null}

      {occurrences.length > 0 ? (
        <Card>
          <AppText variant="headline">Препараты</AppText>
          {occurrences.map((occurrence) => (
            <View key={occurrence.occurrenceId}>
              <AppText variant="callout">
                {formatPlannedTime(occurrence.plannedAt, occurrence.timeZone)} · {occurrence.medicationName}
              </AppText>
              <AppText variant="caption" secondary>
                {formatDose(occurrence.plannedDose, occurrence.unit)} · {STATUS_LABELS[occurrence.status]}
              </AppText>
            </View>
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  entry: { gap: spacing.xs, paddingVertical: spacing.xs },
});
