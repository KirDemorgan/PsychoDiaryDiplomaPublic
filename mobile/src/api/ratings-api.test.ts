import type { HttpClient } from "./http-client";
import { createRatingsApi } from "./ratings-api";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("ratingsApi", () => {
  const request = jest.fn();
  const api = createRatingsApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockReset());

  it("запрашивает оценки периода и возвращает элементы страницы", async () => {
    request.mockResolvedValue({ items: [{ id: "r1" }], page: 0, size: 100, totalElements: 1, totalPages: 1 });

    await expect(api.listForRange("2026-10-18", "2026-10-18")).resolves.toEqual([{ id: "r1" }]);
    expect(request).toHaveBeenCalledWith("/ratings?from=2026-10-18&to=2026-10-18&size=100");
  });

  it("создаёт оценку без If-Match и с ключом идемпотентности", async () => {
    request.mockResolvedValue({});

    await api.save({
      date: "2026-10-18",
      slot: "MORNING",
      values: { anxiety: 4, mood: null, energy: 6 },
      timeZone: "Europe/Moscow",
    });

    expect(request).toHaveBeenCalledWith("/ratings/2026-10-18/MORNING", {
      method: "PUT",
      body: { anxiety: 4, mood: null, energy: 6, timeZone: "Europe/Moscow" },
      headers: { "Idempotency-Key": "generated-key" },
    });
  });

  it("обновляет существующую оценку с If-Match и переданным ключом", async () => {
    request.mockResolvedValue({});

    await api.save({
      date: "2026-10-18",
      slot: "DAY",
      values: { anxiety: 1, mood: 2, energy: 3 },
      timeZone: "Europe/Moscow",
      existing: { version: 3 },
      idempotencyKey: "fixed-key",
    });

    expect(request.mock.calls[0][1].headers).toEqual({ "Idempotency-Key": "fixed-key", "If-Match": '"v3"' });
  });

  it("удаляет оценку с If-Match", async () => {
    request.mockResolvedValue(undefined);

    await api.remove({ date: "2026-10-18", slot: "EVENING", existing: { version: 7 } });

    expect(request).toHaveBeenCalledWith("/ratings/2026-10-18/EVENING", {
      method: "DELETE",
      headers: { "If-Match": '"v7"' },
    });
  });
});
