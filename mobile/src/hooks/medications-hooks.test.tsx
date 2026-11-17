import { renderHook, waitFor } from "@testing-library/react-native";

import { medicationsApi } from "@/api/medications-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import {
  useArchiveMedication,
  useCreateIntake,
  useCreateMedication,
  useCreateSchedule,
  useDeleteMedication,
  useMedication,
  awaitsFirstSchedule,
  useMedications,
  useOccurrences,
  useRemoveIntake,
  useScheduleHistory,
} from "./use-medications";

jest.mock("@/api/medications-api", () => ({
  medicationsApi: {
    list: jest.fn(),
    get: jest.fn(),
    create: jest.fn(),
    createSchedule: jest.fn(),
    scheduleHistory: jest.fn(),
    archive: jest.fn(),
    remove: jest.fn(),
    occurrences: jest.fn(),
    createIntake: jest.fn(),
    removeIntake: jest.fn(),
  },
}));

const api = medicationsApi as jest.Mocked<typeof medicationsApi>;

const schedule = { effectiveFrom: "x", treatmentEndsOn: null, timeZone: "UTC", slots: [] };

describe("medication queries", () => {
  beforeEach(() => jest.resetAllMocks());

  it("загружает список, препарат, историю и приёмы", async () => {
    api.list.mockResolvedValue([]);
    api.get.mockResolvedValue({ id: "m1" } as never);
    api.scheduleHistory.mockResolvedValue([]);
    api.occurrences.mockResolvedValue({ items: [] } as never);
    const wrapper = queryWrapper();

    const list = await renderHook(() => useMedications(true), { wrapper });
    const one = await renderHook(() => useMedication("m1"), { wrapper });
    const history = await renderHook(() => useScheduleHistory("m1"), { wrapper });
    const occurrences = await renderHook(() => useOccurrences("2026-10-18", "2026-10-18", "UTC"), { wrapper });

    await waitFor(() => expect(list.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(one.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(history.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(occurrences.result.current.isSuccess).toBe(true));
    expect(api.list).toHaveBeenCalledWith(true);
    expect(api.occurrences).toHaveBeenCalledWith("2026-10-18", "2026-10-18", "UTC");
  });
});

describe("awaitsFirstSchedule", () => {
  const now = Date.parse("2026-10-24T10:00:00Z");
  const base = { archivedAt: null, currentSchedule: null };
  const med = (overrides: Record<string, unknown>) => ({ ...base, createdAt: "2026-10-24T09:59:30Z", ...overrides }) as never;

  it("ждёт схему у только что созданного препарата", () => {
    expect(awaitsFirstSchedule([med({})], now)).toBe(true);
  });

  it("не опрашивает старый препарат без схемы, архивный и препарат со схемой", () => {
    expect(awaitsFirstSchedule([med({ createdAt: "2026-10-24T09:00:00Z" })], now)).toBe(false);
    expect(awaitsFirstSchedule([med({ archivedAt: "2026-10-24T09:59:40Z" })], now)).toBe(false);
    expect(awaitsFirstSchedule([med({ currentSchedule: {} })], now)).toBe(false);
    expect(awaitsFirstSchedule(undefined, now)).toBe(false);
  });
});

describe("medication mutations", () => {
  beforeEach(() => jest.resetAllMocks());

  it("создание препарата сначала создаёт его, затем схему с версией ответа", async () => {
    api.create.mockResolvedValue({ id: "m1", version: 0 } as never);
    api.createSchedule.mockResolvedValue({} as never);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useCreateMedication(), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync({ name: "Препарат", form: "таблетки", schedule, idempotencyKey: "k" });

    expect(api.create).toHaveBeenCalledWith({ name: "Препарат", form: "таблетки" }, "k");
    expect(api.createSchedule).toHaveBeenCalledWith("m1", 0, schedule);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["medications"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["occurrences"] });
  });

  it("если схема не создалась, ошибка пробрасывается и данные обновляются", async () => {
    api.create.mockResolvedValue({ id: "m1", version: 0 } as never);
    api.createSchedule.mockRejectedValue(new Error("conflict"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useCreateMedication(), { wrapper: queryWrapper(client) });

    await expect(result.current.mutateAsync({ name: "П", schedule })).rejects.toThrow("conflict");

    expect(invalidate).toHaveBeenCalled();
  });

  it("новая версия схемы, архив и удаление идут с версией препарата", async () => {
    api.createSchedule.mockResolvedValue({} as never);
    api.archive.mockResolvedValue({} as never);
    api.remove.mockResolvedValue(undefined);
    const client = createTestQueryClient();
    const removeQueries = jest.spyOn(client, "removeQueries");
    const wrapper = queryWrapper(client);

    const created = await renderHook(() => useCreateSchedule("m1"), { wrapper });
    const archived = await renderHook(() => useArchiveMedication("m1"), { wrapper });
    const removed = await renderHook(() => useDeleteMedication("m1"), { wrapper });
    await created.result.current.mutateAsync({ version: 2, schedule });
    await archived.result.current.mutateAsync(3);
    await removed.result.current.mutateAsync(4);

    expect(api.createSchedule).toHaveBeenCalledWith("m1", 2, schedule);
    expect(api.archive).toHaveBeenCalledWith("m1", 3);
    expect(api.remove).toHaveBeenCalledWith("m1", 4);
    expect(removeQueries).toHaveBeenCalledWith({ queryKey: ["medication", "m1"] });
  });

  it("отметка приёма и её снятие обновляют приёмы", async () => {
    api.createIntake.mockResolvedValue({} as never);
    api.removeIntake.mockResolvedValue(undefined);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const wrapper = queryWrapper(client);
    const body = { kind: "PLANNED", occurrenceId: "o1", status: "SKIPPED" } as const;

    const create = await renderHook(() => useCreateIntake(), { wrapper });
    const remove = await renderHook(() => useRemoveIntake(), { wrapper });
    await create.result.current.mutateAsync({ body, idempotencyKey: "k" });
    await remove.result.current.mutateAsync({ id: "i1", version: 2 });

    expect(api.createIntake).toHaveBeenCalledWith(body, "k");
    expect(api.removeIntake).toHaveBeenCalledWith("i1", 2);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["occurrences"] });
  });
});
