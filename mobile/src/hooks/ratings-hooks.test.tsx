import { renderHook, waitFor } from "@testing-library/react-native";

import { ratingsApi } from "@/api/ratings-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import { useDayRatings } from "./use-day-ratings";
import { useSaveRating } from "./use-save-rating";

jest.mock("@/api/ratings-api", () => ({
  ratingsApi: { listForRange: jest.fn(), save: jest.fn(), remove: jest.fn() },
}));

const api = ratingsApi as jest.Mocked<typeof ratingsApi>;

function setup() {
  const client = createTestQueryClient();
  return { client, wrapper: queryWrapper(client) };
}

describe("useDayRatings", () => {
  it("загружает оценки одного дня", async () => {
    api.listForRange.mockResolvedValue([]);
    const { wrapper } = setup();

    const { result } = await renderHook(() => useDayRatings("2026-10-18"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.listForRange).toHaveBeenCalledWith("2026-10-18", "2026-10-18");
  });
});

describe("useSaveRating", () => {
  const input = {
    date: "2026-10-18",
    slot: "DAY" as const,
    values: { anxiety: 1, mood: 2, energy: 3 },
    timeZone: "Europe/Moscow",
  };

  it("сохраняет оценку и обновляет кэш дня", async () => {
    api.save.mockResolvedValue({} as never);
    const { client, wrapper } = setup();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useSaveRating("2026-10-18"), { wrapper });

    await result.current.save.mutateAsync(input);

    expect(api.save).toHaveBeenCalledWith(input);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["ratings", "2026-10-18"] });
  });

  it("удаляет оценку и обновляет кэш дня", async () => {
    api.remove.mockResolvedValue(undefined);
    const { client, wrapper } = setup();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useSaveRating("2026-10-18"), { wrapper });

    await result.current.remove.mutateAsync({ date: "2026-10-18", slot: "DAY", existing: { version: 1 } });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["ratings", "2026-10-18"] });
  });

  it("обновляет кэш и при ошибке, чтобы подтянуть актуальную версию", async () => {
    api.save.mockRejectedValue(new Error("conflict"));
    const { client, wrapper } = setup();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useSaveRating("2026-10-18"), { wrapper });

    await expect(result.current.save.mutateAsync(input)).rejects.toThrow("conflict");

    expect(invalidate).toHaveBeenCalled();
  });
});
