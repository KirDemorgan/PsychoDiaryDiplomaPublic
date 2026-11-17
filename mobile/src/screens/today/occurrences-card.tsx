import { Link } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import type { Occurrence } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { formatDose, formatPlannedTime } from "@/features/medications/format";
import { STATUS_LABELS } from "@/features/medications/labels";
import { useCreateIntake, useOccurrences, useRemoveIntake } from "@/hooks/use-medications";
import { localDateIn } from "@/lib/dates";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function OccurrencesCard() {
  const theme = useTheme();
  const [timeZone] = useState(deviceTimeZone);
  const [date] = useState(() => localDateIn(timeZone));
  const occurrences = useOccurrences(date, date, timeZone);
  const createIntake = useCreateIntake();
  const removeIntake = useRemoveIntake();
  const [error, setError] = useState<string | null>(null);

  const mark = async (occurrence: Occurrence, status: "TAKEN" | "SKIPPED") => {
    setError(null);
    try {
      await createIntake.mutateAsync({
        body:
          status === "TAKEN"
            ? {
                kind: "PLANNED",
                occurrenceId: occurrence.occurrenceId,
                status,
                actualAt: new Date().toISOString(),
                actualDose: Number(occurrence.plannedDose),
                unit: occurrence.unit,
              }
            : { kind: "PLANNED", occurrenceId: occurrence.occurrenceId, status },
      });
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  const undo = async (occurrence: Occurrence) => {
    if (!occurrence.intake) {
      return;
    }
    setError(null);
    try {
      await removeIntake.mutateAsync({ id: occurrence.intake.id, version: occurrence.intake.version });
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  const busy = createIntake.isPending || removeIntake.isPending;

  return (
    <Card>
      <AppText variant="headline">Приёмы сегодня</AppText>

      {occurrences.isPending ? <ActivityIndicator accessibilityLabel="Загрузка приёмов" /> : null}

      {occurrences.isError ? (
        <>
          <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
            {describeError(occurrences.error)}
          </AppText>
          <Button title="Повторить" variant="secondary" onPress={() => occurrences.refetch()} />
        </>
      ) : null}

      {occurrences.isSuccess && occurrences.data.items.length === 0 ? (
        <AppText secondary>На сегодня приёмов нет.</AppText>
      ) : null}

      {occurrences.data?.items.map((occurrence) => (
        <View key={occurrence.occurrenceId} style={styles.item}>
          <View style={styles.info}>
            <AppText variant="callout">
              {formatPlannedTime(occurrence.plannedAt, occurrence.timeZone)} · {occurrence.medicationName}
            </AppText>
            <AppText variant="caption" secondary>
              {formatDose(occurrence.plannedDose, occurrence.unit)} · {STATUS_LABELS[occurrence.status]}
            </AppText>
          </View>
          {occurrence.status === "NO_DATA" ? (
            <View style={styles.actions}>
              <Button title="Принял" onPress={() => void mark(occurrence, "TAKEN")} disabled={busy} />
              <Button title="Пропустил" variant="secondary" onPress={() => void mark(occurrence, "SKIPPED")} disabled={busy} />
            </View>
          ) : (
            <Button title="Снять отметку" variant="secondary" onPress={() => void undo(occurrence)} disabled={busy} />
          )}
        </View>
      ))}

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Link href="/medications" asChild>
        <Button title="Препараты" variant="secondary" />
      </Link>
    </Card>
  );
}

const styles = StyleSheet.create({
  item: { gap: spacing.sm },
  info: { gap: 2 },
  actions: { gap: spacing.sm },
});
