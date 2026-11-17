import { randomUUID } from "expo-crypto";
import { useRouter } from "expo-router";
import { useState } from "react";

import { describeError } from "@/api/errors";
import type { CreateScheduleInput } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { useCreateMedication } from "@/hooks/use-medications";

import { ScheduleEditor } from "./schedule-editor";

const NAME_MAX_LENGTH = 200;

export function NewMedicationScreen() {
  const router = useRouter();
  const create = useCreateMedication();
  const [idempotencyKey] = useState(randomUUID);
  const [name, setName] = useState("");
  const [form, setForm] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async (schedule: CreateScheduleInput) => {
    const trimmed = name.trim();
    if (trimmed === "" || trimmed.length > NAME_MAX_LENGTH) {
      setNameError("Введите название препарата");
      return;
    }
    setNameError(null);
    setError(null);
    try {
      await create.mutateAsync({ name: trimmed, form: form.trim() || undefined, schedule, idempotencyKey });
      router.back();
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  return (
    <Screen>
      <AppText variant="caption" secondary>
        Назначение вводите так, как его дал врач. Приложение не подсказывает дозы.
      </AppText>
      <TextField label="Название" value={name} onChangeText={setName} error={nameError ?? undefined} autoFocus />
      <TextField label="Форма (необязательно)" value={form} onChangeText={setForm} />
      <ScheduleEditor
        submitLabel="Сохранить препарат"
        busy={create.isPending}
        serverError={error}
        onSubmit={(schedule) => {
          void submit(schedule);
        }}
      />
    </Screen>
  );
}
