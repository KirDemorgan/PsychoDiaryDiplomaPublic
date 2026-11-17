import { act, renderHook, waitFor } from "@testing-library/react-native";

import { entriesApi } from "@/api/entries-api";
import { createTestQueryClient, queryWrapper } from "@/test/query-wrapper";

import { useEntries, useEntry } from "./use-entries";
import { useCreateEntry, useDeleteEntry, useUpdateEntry } from "./use-entry-mutations";

jest.mock("@/api/entries-api", () => ({
  entriesApi: { list: jest.fn(), get: jest.fn(), create: jest.fn(), update: jest.fn(), remove: jest.fn() },
}));

const api = entriesApi as jest.Mocked<typeof entriesApi>;

const page = (pageNumber: number, totalPages: number) => ({
  items: [{ id: `e${pageNumber}` }] as never[],
  page: pageNumber,
  size: 20,
  totalElements: 40,
  totalPages,
});

describe("useEntries", () => {
  beforeEach(() => jest.resetAllMocks());

  it("загружает страницы по очереди и останавливается на последней", async () => {
    api.list.mockResolvedValueOnce(page(0, 2)).mockResolvedValueOnce(page(1, 2));
    const { result } = await renderHook(() => useEntries("  сон "), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.list).toHaveBeenCalledWith({ q: "сон", page: 0 });
    expect(result.current.hasNextPage).toBe(true);

    await act(async () => {
      await result.current.fetchNextPage();
    });

    expect(api.list).toHaveBeenLastCalledWith({ q: "сон", page: 1 });
    await waitFor(() => expect(result.current.hasNextPage).toBe(false));
  });

  it("не передаёт пустой поисковый запрос", async () => {
    api.list.mockResolvedValue(page(0, 1));
    const { result } = await renderHook(() => useEntries("   "), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.list).toHaveBeenCalledWith({ q: undefined, page: 0 });
  });
});

describe("useEntry", () => {
  it("загружает запись по id", async () => {
    api.get.mockResolvedValue({ id: "e1" } as never);
    const { result } = await renderHook(() => useEntry("e1"), { wrapper: queryWrapper() });

    await waitFor(() => expect(result.current.data).toEqual({ id: "e1" }));
  });
});

describe("entry mutations", () => {
  beforeEach(() => jest.resetAllMocks());

  it("создание обновляет ленту", async () => {
    api.create.mockResolvedValue({} as never);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useCreateEntry(), { wrapper: queryWrapper(client) });
    const body = { text: "т", occurredAt: "x", timeZone: "UTC" };

    await result.current.mutateAsync({ body, idempotencyKey: "k" });

    expect(api.create).toHaveBeenCalledWith(body, "k");
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entries"] });
  });

  it("правка обновляет ленту и запись, в том числе при ошибке", async () => {
    api.update.mockRejectedValue(new Error("conflict"));
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const { result } = await renderHook(() => useUpdateEntry("e1"), { wrapper: queryWrapper(client) });

    await expect(result.current.mutateAsync({ version: 1, body: { text: "н" } })).rejects.toThrow("conflict");

    expect(api.update).toHaveBeenCalledWith("e1", 1, { text: "н" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entries"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entry", "e1"] });
  });

  it("удаление убирает запись из кэша и обновляет ленту", async () => {
    api.remove.mockResolvedValue(undefined);
    const client = createTestQueryClient();
    const invalidate = jest.spyOn(client, "invalidateQueries");
    const removeQueries = jest.spyOn(client, "removeQueries");
    const { result } = await renderHook(() => useDeleteEntry("e1"), { wrapper: queryWrapper(client) });

    await result.current.mutateAsync(3);

    expect(api.remove).toHaveBeenCalledWith("e1", 3);
    expect(removeQueries).toHaveBeenCalledWith({ queryKey: ["entry", "e1"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["entries"] });
  });
});
