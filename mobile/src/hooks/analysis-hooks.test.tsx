import { renderHook, waitFor } from "@testing-library/react-native";

import { analysisApi } from "@/api/analysis-api";
import { settingsApi } from "@/api/settings-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import { ANALYSIS_POLL_MS, analysisKey, useAnalysis, useStartAnalysis, useSubmitTagReview } from "./use-analysis";
import { settingsKey, useSettings, useUpdateSettings } from "./use-settings";

jest.mock("@/api/analysis-api", () => ({
  analysisApi: { get: jest.fn(), start: jest.fn(), submitReview: jest.fn() },
}));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn(), update: jest.fn() } }));

const analyses = analysisApi as jest.Mocked<typeof analysisApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;

const analysis = (status: string) => ({ id: "a1", entryId: "e1", status }) as never;

describe("useAnalysis", () => {
  beforeEach(() => jest.resetAllMocks());

  it("не запрашивает анализ без идентификатора", async () => {
    await renderHook(() => useAnalysis("e1", undefined), { wrapper: queryWrapper() });

    expect(analyses.get).not.toHaveBeenCalled();
  });

  it("опрашивает, пока анализ в очереди или выполняется, и останавливается на итоговом статусе", async () => {
    analyses.get.mockResolvedValue(analysis("RUNNING"));
    const client = createTestQueryClient();
    const { result } = await renderHook(() => useAnalysis("e1", "a1"), { wrapper: queryWrapper(client) });
    await waitFor(() => expect(result.current.data).toBeDefined());

    const query = client.getQueryCache().find({ queryKey: analysisKey("e1", "a1") })!;
    const interval = (query.options as { refetchInterval: (q: unknown) => number | false }).refetchInterval;

    expect(interval({ state: { data: analysis("QUEUED") } })).toBe(ANALYSIS_POLL_MS);
    expect(interval({ state: { data: analysis("RUNNING") } })).toBe(ANALYSIS_POLL_MS);
    expect(interval({ state: { data: analysis("SUCCEEDED") } })).toBe(false);
    expect(interval({ state: { data: undefined } })).toBe(false);
  });
});

describe("analysis mutations", () => {
  beforeEach(() => jest.resetAllMocks());

  it("запуск кладёт анализ в кэш и обновляет запись", async () => {
    analyses.start.mockResolvedValue(analysis("QUEUED"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useStartAnalysis("e1"), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync({ revision: 2 });

    expect(analyses.start).toHaveBeenCalledWith("e1", 2, undefined);
    expect(client.getQueryData(analysisKey("e1", "a1"))).toMatchObject({ status: "QUEUED" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entry", "e1"] });
  });

  it("подтверждение тегов обновляет запись и ленту", async () => {
    analyses.submitReview.mockResolvedValue({ id: "e1", reviewComplete: true } as never);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useSubmitTagReview("e1"), { wrapper: queryWrapper(client) });
    const body = { revision: 1, complete: true, decisions: [], manualTags: [] };

    await result.current.mutateAsync({ version: 3, body });

    expect(analyses.submitReview).toHaveBeenCalledWith("e1", 3, body, undefined);
    expect(client.getQueryData(["entry", "e1"])).toMatchObject({ reviewComplete: true });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entries"] });
  });

  it("при ошибке подтверждения перезагружает запись", async () => {
    analyses.submitReview.mockRejectedValue(new Error("stale"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useSubmitTagReview("e1"), { wrapper: queryWrapper(client) });

    await expect(
      result.current.mutateAsync({ version: 1, body: { revision: 1, complete: true, decisions: [], manualTags: [] } }),
    ).rejects.toThrow("stale");

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entry", "e1"] });
  });
});

describe("settings hooks", () => {
  beforeEach(() => jest.resetAllMocks());

  it("загружает настройки", async () => {
    settings.get.mockResolvedValue({ aiEnabled: true } as never);
    const { result } = await renderHook(() => useSettings(), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.data).toEqual({ aiEnabled: true }));
  });

  it("после обновления кладёт ответ сервера в кэш", async () => {
    settings.update.mockResolvedValue({ aiEnabled: true, version: 2 } as never);
    const client = createTestQueryClient();
    const { result } = await renderHook(() => useUpdateSettings(), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync({ version: 1, body: { aiEnabled: true } });

    expect(client.getQueryData(settingsKey)).toEqual({ aiEnabled: true, version: 2 });
  });

  it("после обновления настроек перезагружает согласие на голос: у них общая версия", async () => {
    settings.update.mockResolvedValue({ aiEnabled: true, version: 2 } as never);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useUpdateSettings(), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync({ version: 1, body: { aiEnabled: true } });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["speech-consent"] });
  });

  it("при ошибке перезагружает настройки", async () => {
    settings.update.mockRejectedValue(new Error("conflict"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useUpdateSettings(), { wrapper: queryWrapper(client) });

    await expect(result.current.mutateAsync({ version: 1, body: { aiEnabled: true } })).rejects.toThrow("conflict");

    expect(invalidate).toHaveBeenCalledWith({ queryKey: settingsKey });
  });
});
