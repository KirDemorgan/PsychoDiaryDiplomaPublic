import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { medicationsApi } from "@/api/medications-api";
import type { CreateIntakeInput, CreateScheduleInput, Medication } from "@/api/medications-types";

export const medicationsKey = ["medications"] as const;
export const medicationKey = (id: string) => ["medication", id] as const;
export const occurrencesKey = (from: string, to: string) => ["occurrences", from, to] as const;

export const SCHEDULE_PENDING_WINDOW_MS = 2 * 60_000;
export const SCHEDULE_PENDING_POLL_MS = 10_000;

export function awaitsFirstSchedule(medications: readonly Medication[] | undefined, now: number): boolean {
  return (medications ?? []).some(
    (medication) =>
      medication.currentSchedule === null &&
      medication.archivedAt === null &&
      now - new Date(medication.createdAt).getTime() < SCHEDULE_PENDING_WINDOW_MS,
  );
}

export function useMedications(archived = false) {
  return useQuery({
    queryKey: [...medicationsKey, { archived }],
    queryFn: () => medicationsApi.list(archived),
    refetchInterval: (query) =>
      awaitsFirstSchedule(query.state.data, Date.now()) ? SCHEDULE_PENDING_POLL_MS : false,
  });
}

export function useMedication(id: string) {
  return useQuery({ queryKey: medicationKey(id), queryFn: () => medicationsApi.get(id) });
}

export function useScheduleHistory(id: string) {
  return useQuery({
    queryKey: [...medicationKey(id), "schedule-history"],
    queryFn: () => medicationsApi.scheduleHistory(id),
  });
}

export function useOccurrences(from: string, to: string, timeZone: string) {
  return useQuery({
    queryKey: occurrencesKey(from, to),
    queryFn: () => medicationsApi.occurrences(from, to, timeZone),
  });
}

function useInvalidateMedicationData() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: medicationsKey }),
      queryClient.invalidateQueries({ queryKey: ["medication"] }),
      queryClient.invalidateQueries({ queryKey: ["occurrences"] }),
    ]);
}

export function useCreateMedication() {
  const invalidate = useInvalidateMedicationData();
  return useMutation({
    mutationFn: async ({
      name,
      form,
      schedule,
      idempotencyKey,
    }: {
      name: string;
      form?: string;
      schedule: CreateScheduleInput;
      idempotencyKey?: string;
    }) => {
      const medication = await medicationsApi.create({ name, form }, idempotencyKey);
      await medicationsApi.createSchedule(medication.id, medication.version, schedule);
      return medication;
    },
    onSettled: invalidate,
  });
}

export function useCreateSchedule(id: string) {
  const invalidate = useInvalidateMedicationData();
  return useMutation({
    mutationFn: ({ version, schedule }: { version: number; schedule: CreateScheduleInput }) =>
      medicationsApi.createSchedule(id, version, schedule),
    onSettled: invalidate,
  });
}

export function useArchiveMedication(id: string) {
  const invalidate = useInvalidateMedicationData();
  return useMutation({
    mutationFn: (version: number) => medicationsApi.archive(id, version),
    onSettled: invalidate,
  });
}

export function useDeleteMedication(id: string) {
  const invalidate = useInvalidateMedicationData();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (version: number) => medicationsApi.remove(id, version),
    onSuccess: () => queryClient.removeQueries({ queryKey: medicationKey(id) }),
    onSettled: invalidate,
  });
}

export function useCreateIntake() {
  const invalidate = useInvalidateMedicationData();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateIntakeInput; idempotencyKey?: string }) =>
      medicationsApi.createIntake(body, idempotencyKey),
    onSettled: invalidate,
  });
}

export function useRemoveIntake() {
  const invalidate = useInvalidateMedicationData();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => medicationsApi.removeIntake(id, version),
    onSettled: invalidate,
  });
}
