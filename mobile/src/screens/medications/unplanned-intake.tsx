import { useState } from "react";
import { StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import type { DoseUnit } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Chip } from "@/components/chip";
import { TextField } from "@/components/text-field";
import { UNITS, UNIT_LABELS } from "@/features/medications/labels";
import { isDoseValid, parseDose } from "@/features/medications/schedule-form";
import { useCreateIntake } from "@/hooks/use-medications";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

interface UnplannedIntakeProps {
  medicationId: string;
  defaultUnit: DoseUnit;
}

export function UnplannedIntake({ medicationId, defaultUnit }: UnplannedIntakeProps) {
  const theme = useTheme();
  const create = useCreateIntake();
  const [open, setOpen] = useState(false);
  const [dose, setDose] = useState("");
  const [unit, setUnit] = useState<DoseUnit>(defaultUnit);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    setMessage(null);
    try {
      await create.mutateAsync({
        body: {
          kind: "UNPLANNED",
          medicationId,
          status: "TAKEN",
          actualAt: new Date().toISOString(),
          actualDose: parseDose(dose),
          unit,
        },
      });
      setMessage("Внеплановый приём записан.");
      setDose("");
      setOpen(false);
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  if (!open) {
    return (
      <>
        <Button title="Внеплановый приём" variant="secondary" onPress={() => setOpen(true)} />
        {message ? <AppText variant="callout">{message}</AppText> : null}
      </>
    );
  }

  return (
    <Card>
      <AppText variant="headline">Внеплановый приём</AppText>
      <AppText variant="caption" secondary>
        Записывается с текущим временем. Не входит в выполнение схемы.
      </AppText>
      <TextField label="Доза" value={dose} onChangeText={setDose} keyboardType="decimal-pad" />
      <View style={styles.chips} accessibilityLabel="Единица дозы">
        {UNITS.map((value) => (
          <Chip key={value} label={UNIT_LABELS[value]} selected={unit === value} onPress={() => setUnit(value)} />
        ))}
      </View>
      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}
      <Button
        title="Записать приём"
        disabled={!isDoseValid(dose)}
        loading={create.isPending}
        onPress={() => {
          void submit();
        }}
      />
      <Button title="Отмена" variant="secondary" onPress={() => setOpen(false)} />
    </Card>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
});
