import { randomUUID } from "expo-crypto";

import { httpClient, type HttpClient } from "./http-client";
import type {
  CreateIntakeInput,
  CreateScheduleInput,
  Intake,
  Medication,
  OccurrenceList,
  ScheduleVersion,
} from "./medications-types";
import type { PageResponse } from "./ratings-types";
import { formatEtag } from "./versions";

const MAX_PAGE_SIZE = 100;

export function createMedicationsApi(client: HttpClient) {
  const ifMatch = (version: number) => ({ "If-Match": formatEtag(version) });
  const idempotency = (key: string | undefined) => ({ "Idempotency-Key": key ?? randomUUID() });

  return {
    list: async (archived = false): Promise<Medication[]> => {
      const query = new URLSearchParams({ archived: String(archived), size: String(MAX_PAGE_SIZE) });
      return (await client.request<PageResponse<Medication>>(`/medications?${query}`)).items;
    },

    get: (id: string) => client.request<Medication>(`/medications/${id}`),

    create: (body: { name: string; form?: string }, idempotencyKey?: string) =>
      client.request<Medication>("/medications", { method: "POST", body, headers: idempotency(idempotencyKey) }),

    update: (id: string, version: number, body: { name?: string; form?: string | null }) =>
      client.request<Medication>(`/medications/${id}`, { method: "PATCH", body, headers: ifMatch(version) }),

    scheduleHistory: (id: string) => client.request<ScheduleVersion[]>(`/medications/${id}/schedule-versions`),

    createSchedule: (id: string, version: number, body: CreateScheduleInput, idempotencyKey?: string) =>
      client.request<ScheduleVersion>(`/medications/${id}/schedule-versions`, {
        method: "POST",
        body,
        headers: { ...ifMatch(version), ...idempotency(idempotencyKey) },
      }),

    archive: (id: string, version: number, idempotencyKey?: string) =>
      client.request<Medication>(`/medications/${id}/archive`, {
        method: "POST",
        headers: { ...ifMatch(version), ...idempotency(idempotencyKey) },
      }),

    remove: (id: string, version: number) =>
      client.request<void>(`/medications/${id}`, { method: "DELETE", headers: ifMatch(version) }),

    occurrences: (from: string, to: string, timeZone: string) => {
      const query = new URLSearchParams({ from, to, timeZone });
      return client.request<OccurrenceList>(`/medication-occurrences?${query}`);
    },

    createIntake: (body: CreateIntakeInput, idempotencyKey?: string) =>
      client.request<Intake>("/intakes", { method: "POST", body, headers: idempotency(idempotencyKey) }),

    removeIntake: (id: string, version: number) =>
      client.request<void>(`/intakes/${id}`, { method: "DELETE", headers: ifMatch(version) }),
  };
}

export type MedicationsApi = ReturnType<typeof createMedicationsApi>;

export const medicationsApi = createMedicationsApi(httpClient);
