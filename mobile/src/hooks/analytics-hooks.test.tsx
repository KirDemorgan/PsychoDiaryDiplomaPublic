import { renderHook, waitFor } from "@testing-library/react-native";

import { analyticsApi } from "@/api/analytics-api";
import { queryWrapper } from "@/test/query-wrapper";

import { useComparison, useOverview } from "./use-analytics";

jest.mock("@/api/analytics-api", () => ({ analyticsApi: { overview: jest.fn(), comparison: jest.fn() } }));

const api = analyticsApi as jest.Mocked<typeof analyticsApi>;

describe("analytics hooks", () => {
  it("загружают обзор и сравнение с параметрами запроса", async () => {
    api.overview.mockResolvedValue({ coverage: {} } as never);
    api.comparison.mockResolvedValue({ status: "SUFFICIENT" } as never);
    const wrapper = queryWrapper();

    const overview = await renderHook(() => useOverview("2026-10-12", "2026-10-18", "UTC"), { wrapper });
    const comparison = await renderHook(
      () => useComparison("2026-10-12", "2026-10-18", "UTC", "CAFFEINE", "MOOD"),
      { wrapper },
    );

    await waitFor(() => expect(overview.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(comparison.result.current.isSuccess).toBe(true));
    expect(api.overview).toHaveBeenCalledWith("2026-10-12", "2026-10-18", "UTC");
    expect(api.comparison).toHaveBeenCalledWith("2026-10-12", "2026-10-18", "UTC", "CAFFEINE", "MOOD");
  });
});
