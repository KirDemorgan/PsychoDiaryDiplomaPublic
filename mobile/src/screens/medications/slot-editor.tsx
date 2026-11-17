import { StyleSheet, View } from "react-native";

import type { DayOfWeek } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Chip } from "@/components/chip";
import { Card } from "@/components/card";
import { TextField } from "@/components/text-field";
import { TimeField } from "@/components/time-field";
import { DAYS, DAY_LABELS, UNITS, UNIT_LABELS } from "@/features/medications/labels";
import type { SlotDraft } from "@/features/medications/schedule-form";
import { spacing } from "@/theme/tokens";

interface SlotEditorProps {
  slot: SlotDraft;
  index: number;
  canRemove: boolean;
  onChange: (slot: SlotDraft) => void;
  onRemove: () => void;
}

export function SlotEditor({ slot, index, canRemove, onChange, onRemove }: SlotEditorProps) {
  const toggleDay = (day: DayOfWeek) =>
    onChange({
      ...slot,
      days: slot.days.includes(day) ? slot.days.filter((value) => value !== day) : [...slot.days, day],
    });

  return (
    <Card>
      <TimeField label={`Время приёма ${index + 1}`} value={slot.time} onChange={(time) => onChange({ ...slot, time })} />

      <AppText variant="caption" secondary>
        Дни
      </AppText>
      <View style={styles.chips} accessibilityLabel="Дни приёма">
        {DAYS.map((day) => (
          <Chip key={day} label={DAY_LABELS[day]} selected={slot.days.includes(day)} onPress={() => toggleDay(day)} />
        ))}
      </View>

      <TextField
        label="Доза"
        value={slot.dose}
        onChangeText={(dose) => onChange({ ...slot, dose })}
        keyboardType="decimal-pad"
      />
      <View style={styles.chips} accessibilityLabel="Единица дозы">
        {UNITS.map((unit) => (
          <Chip key={unit} label={UNIT_LABELS[unit]} selected={slot.unit === unit} onPress={() => onChange({ ...slot, unit })} />
        ))}
      </View>

      {canRemove ? <Button title="Убрать приём" variant="destructive" onPress={onRemove} /> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
});
