import { Link } from "expo-router";
import { Pressable, StyleSheet } from "react-native";

import type { Medication } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Card } from "@/components/card";
import { describeSlot } from "@/features/medications/format";
import { formatDateOnly } from "@/lib/format";
import { spacing } from "@/theme/tokens";

export function MedicationCard({ medication }: { medication: Medication }) {
  const schedule = medication.currentSchedule;
  return (
    <Link href={{ pathname: "/medications/[id]", params: { id: medication.id } }} asChild>
      <Pressable accessibilityRole="link" style={styles.pressable}>
        <Card style={styles.card}>
          <AppText variant="headline">{medication.name}</AppText>
          {medication.form ? (
            <AppText variant="caption" secondary>
              {medication.form}
            </AppText>
          ) : null}
          {schedule === null ? (
            <AppText variant="caption" secondary>
              Схема не задана
            </AppText>
          ) : (
            schedule.slots.map((slot) => (
              <AppText key={slot.id} variant="callout">
                {describeSlot(slot)}
              </AppText>
            ))
          )}
          {schedule?.treatmentEndsOn ? (
            <AppText variant="caption" secondary>
              Курс до {formatDateOnly(schedule.treatmentEndsOn)}
            </AppText>
          ) : null}
          {medication.archivedAt ? (
            <AppText variant="caption" secondary>
              В архиве
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
