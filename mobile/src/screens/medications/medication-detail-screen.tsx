import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, Alert } from "react-native";

import { ApiError, describeError, isApiError } from "@/api/errors";
import type { CreateScheduleInput } from "@/api/medications-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { describeSlot, upcomingSchedule } from "@/features/medications/format";
import {
  useArchiveMedication,
  useCreateSchedule,
  useDeleteMedication,
  useMedication,
  useScheduleHistory,
} from "@/hooks/use-medications";
import { currentTime, msUntil } from "@/lib/dates";
import { formatDateOnly, formatDay } from "@/lib/format";
import { useTheme } from "@/theme/use-theme";

import { ScheduleEditor } from "./schedule-editor";
import { UnplannedIntake } from "./unplanned-intake";

const CONFLICT_MESSAGE = "Препарат изменён на другом устройстве. Данные обновлены, повторите действие.";

export function MedicationDetailScreen({ id }: { id: string }) {
  const theme = useTheme();
  const router = useRouter();
  const medication = useMedication(id);
  const history = useScheduleHistory(id);
  const createSchedule = useCreateSchedule(id);
  const archive = useArchiveMedication(id);
  const remove = useDeleteMedication(id);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const upcoming = upcomingSchedule(history.data, currentTime());
  const upcomingStart = upcoming?.effectiveFrom;
  const { refetch: refetchMedication } = medication;
  const { refetch: refetchHistory } = history;

  useEffect(() => {
    if (!upcomingStart) {
      return;
    }
    const delay = msUntil(upcomingStart) + 1000;
    const timer = setTimeout(() => {
      void refetchMedication();
      void refetchHistory();
    }, delay);
    return () => clearTimeout(timer);
  }, [upcomingStart, refetchMedication, refetchHistory]);

  if (medication.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка препарата" />
      </Screen>
    );
  }
  if (medication.isError) {
    const missing = isApiError(medication.error) && medication.error.status === 404;
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {missing ? "Препарат не найден." : describeError(medication.error)}
        </AppText>
        {missing ? null : <Button title="Повторить" variant="secondary" onPress={() => medication.refetch()} />}
      </Screen>
    );
  }

  const value = medication.data;
  const schedule = value.currentSchedule;
  const shown = upcoming ?? schedule;
  const archivedAlready = value.archivedAt !== null;

  const handleError = (failure: unknown) =>
    setError(failure instanceof ApiError && failure.status === 412 ? CONFLICT_MESSAGE : describeError(failure));

  const saveSchedule = async (input: CreateScheduleInput) => {
    setError(null);
    try {
      await createSchedule.mutateAsync({ version: value.version, schedule: input });
      setEditing(false);
    } catch (failure) {
      handleError(failure);
    }
  };

  const confirmArchive = () =>
    Alert.alert("Архивировать препарат?", "Будущие приёмы исчезнут, история отметок сохранится.", [
      { text: "Отмена", style: "cancel" },
      { text: "Архивировать", onPress: () => archive.mutateAsync(value.version).catch(handleError) },
    ]);

  const confirmDelete = () =>
    Alert.alert(
      "Удалить препарат?",
      "Препарат, его схемы и все отметки приёма будут удалены без возможности восстановления.",
      [
        { text: "Отмена", style: "cancel" },
        {
          text: "Удалить",
          style: "destructive",
          onPress: () => remove.mutateAsync(value.version).then(() => router.back(), handleError),
        },
      ],
    );

  return (
    <Screen>
      <AppText variant="title">{value.name}</AppText>
      {value.form ? <AppText secondary>{value.form}</AppText> : null}

      <Card>
        <AppText variant="headline">Текущая схема</AppText>
        {shown === null ? <AppText secondary>Схема не задана.</AppText> : null}
        {shown?.slots.map((slot) => (
          <AppText key={slot.id}>{describeSlot(slot)}</AppText>
        ))}
        {shown?.treatmentEndsOn ? (
          <AppText variant="caption" secondary>
            Курс до {formatDateOnly(shown.treatmentEndsOn)}
          </AppText>
        ) : null}
      </Card>

      {archivedAlready ? (
        <AppText secondary>Препарат в архиве: новые схемы добавить нельзя.</AppText>
      ) : editing ? (
        <>
          <AppText variant="caption" secondary>
            Новая схема начнёт действовать с текущего момента. Прошлая история не изменится.
          </AppText>
          <ScheduleEditor
            submitLabel="Сохранить схему"
            busy={createSchedule.isPending}
            serverError={error}
            onSubmit={(input) => {
              void saveSchedule(input);
            }}
          />
          <Button title="Отмена" variant="secondary" onPress={() => setEditing(false)} />
        </>
      ) : (
        <Button
          title="Изменить схему"
          onPress={() => {
            setError(null);
            setEditing(true);
          }}
        />
      )}

      {archivedAlready ? null : (
        <UnplannedIntake medicationId={value.id} defaultUnit={schedule?.slots[0]?.unit ?? "TABLET"} />
      )}

      {history.data && history.data.length > 1 ? (
        <Card>
          <AppText variant="headline">История схем</AppText>
          {history.data
            .slice()
            .reverse()
            .map((version) => (
              <AppText key={version.id} variant="callout" secondary>
                С {formatDay(version.effectiveFrom, version.timeZone)}:{" "}
                {version.slots.map((slot) => describeSlot(slot)).join("; ")}
              </AppText>
            ))}
        </Card>
      ) : null}

      {error && !editing ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      {archivedAlready ? null : (
        <Button title="Архивировать" variant="secondary" onPress={confirmArchive} loading={archive.isPending} />
      )}
      <Button title="Удалить" variant="destructive" onPress={confirmDelete} loading={remove.isPending} />
    </Screen>
  );
}
