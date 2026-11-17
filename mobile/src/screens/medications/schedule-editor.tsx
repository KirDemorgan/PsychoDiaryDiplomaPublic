import { randomUUID } from "expo-crypto";
import { useState } from "react";
import { Switch, View } from "react-native";

import type { CreateScheduleInput } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { DateField } from "@/components/date-field";
import { newSlotDraft, toScheduleInput, validateSlots, type SlotDraft } from "@/features/medications/schedule-form";
import { localDateIn } from "@/lib/dates";
import { offsetDateTimeIn } from "@/lib/offset-date-time";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { SlotEditor } from "./slot-editor";

const START_DELAY_MS = 30_000;

interface ScheduleEditorProps {
  submitLabel: string;
  busy: boolean;
  serverError: string | null;
  onSubmit: (schedule: CreateScheduleInput) => void;
}

export function ScheduleEditor({ submitLabel, busy, serverError, onSubmit }: ScheduleEditorProps) {
  const theme = useTheme();
  const [slots, setSlots] = useState<SlotDraft[]>(() => [newSlotDraft(randomUUID())]);
  const [limited, setLimited] = useState(false);
  const [endsOn, setEndsOn] = useState(() => new Date());
  const [validation, setValidation] = useState<string | null>(null);

  const submit = () => {
    const problem = validateSlots(slots);
    setValidation(problem);
    if (problem) {
      return;
    }
    const timeZone = deviceTimeZone();
    onSubmit(
      toScheduleInput(
        slots,
        offsetDateTimeIn(timeZone, new Date(Date.now() + START_DELAY_MS)),
        timeZone,
        limited ? localDateIn(timeZone, endsOn) : null,
      ),
    );
  };

  const message = validation ?? serverError;

  return (
    <View style={{ gap: spacing.lg }}>
      {slots.map((slot, index) => (
        <SlotEditor
          key={slot.key}
          slot={slot}
          index={index}
          canRemove={slots.length > 1}
          onChange={(next) => setSlots((current) => current.map((item) => (item.key === slot.key ? next : item)))}
          onRemove={() => setSlots((current) => current.filter((item) => item.key !== slot.key))}
        />
      ))}

      <Button
        title="Добавить время приёма"
        variant="secondary"
        onPress={() => setSlots((current) => [...current, newSlotDraft(randomUUID())])}
      />

      <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
        <AppText>Ограничить курс датой</AppText>
        <Switch
          accessibilityLabel="Ограничить курс датой"
          value={limited}
          onValueChange={setLimited}
          trackColor={{ true: theme.primary }}
        />
      </View>
      {limited ? (
        <DateField label="Последний день курса" value={endsOn} minimumDate={new Date()} onChange={setEndsOn} />
      ) : null}

      {message ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {message}
        </AppText>
      ) : null}

      <Button title={submitLabel} onPress={submit} loading={busy} />
    </View>
  );
}
