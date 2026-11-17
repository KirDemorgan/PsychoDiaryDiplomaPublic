import { renderHook, waitFor } from "@testing-library/react-native";

import { reportsApi } from "@/api/reports-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import { REPORT_POLL_MS, reportKey, useCancelReport, useCreateReport, useDeleteReport, useReport, useReports } from "./use-reports";

jest.mock("@/api/reports-api", () => ({
  reportsApi: { list: jest.fn(), get: jest.fn(), create: jest.fn(), cancel: jest.fn(), remove: jest.fn() },
}));

const api = reportsApi as jest.Mocked<typeof reportsApi>;
const report = (status: string) => ({ id: "r1", status }) as never;

describe("reports hooks", () => {
  beforeEach(() => jest.resetAllMocks());

  it("загружает список отчётов", async () => {
    api.list.mockResolvedValue([]);
    const { result } = await renderHook(() => useReports(), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("опрашивает отчёт, пока он в очереди или готовится", async () => {
    api.get.mockResolvedValue(report("QUEUED"));
    const client = createTestQueryClient();
    const { result } = await renderHook(() => useReport("r1"), { wrapper: queryWrapper(client) });
    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = client.getQueryCache().find({ queryKey: reportKey("r1") })!;
    const interval = (query.options as { refetchInterval: (q: unknown) => number | false }).refetchInterval;

    expect(interval({ state: { data: report("QUEUED") } })).toBe(REPORT_POLL_MS);
    expect(interval({ state: { data: report("RUNNING") } })).toBe(REPORT_POLL_MS);
    expect(interval({ state: { data: report("READY") } })).toBe(false);
    expect(interval({ state: { data: undefined } })).toBe(false);
  });

  it("создание кладёт отчёт в кэш и обновляет список", async () => {
    api.create.mockResolvedValue(report("QUEUED"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useCreateReport(), { wrapper: queryWrapper(client) });
    const body = { from: "a", to: "b", timeZone: "UTC", sections: [], entrySelection: "NONE", medicationSelection: "NONE" } as const;

    await result.current.mutateAsync({ body: { ...body, sections: [] }, idempotencyKey: "k" });

    expect(api.create).toHaveBeenCalledWith({ ...body, sections: [] }, "k");
    expect(client.getQueryData(reportKey("r1"))).toMatchObject({ status: "QUEUED" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["reports"] });
  });

  it("отмена обновляет отчёт, удаление убирает его из кэша", async () => {
    api.cancel.mockResolvedValue(report("CANCELLED"));
    api.remove.mockResolvedValue(undefined);
    const client = createTestQueryClient();
    const removeQueries = jest.spyOn(client, "removeQueries");
    const wrapper = queryWrapper(client);
    const cancel = await renderHook(() => useCancelReport("r1"), { wrapper });
    const remove = await renderHook(() => useDeleteReport("r1"), { wrapper });

    await cancel.result.current.mutateAsync();
    await remove.result.current.mutateAsync(2);

    expect(client.getQueryData(reportKey("r1"))).toBeUndefined();
    expect(api.remove).toHaveBeenCalledWith("r1", 2);
    expect(removeQueries).toHaveBeenCalledWith({ queryKey: ["report", "r1"] });
  });
});
