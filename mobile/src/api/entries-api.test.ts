import { createEntriesApi } from "./entries-api";
import type { HttpClient } from "./http-client";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("entriesApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createEntriesApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockClear());

  it("запрашивает ленту с размером страницы по умолчанию", async () => {
    await api.list();

    expect(request).toHaveBeenCalledWith("/entries?size=20");
  });

  it("передаёт поиск и страницу, пропуская пустые параметры", async () => {
    await api.list({ q: "сон", page: 2, from: undefined, to: "" });

    expect(request).toHaveBeenCalledWith("/entries?size=20&q=%D1%81%D0%BE%D0%BD&page=2");
  });

  it("читает запись по id", async () => {
    await api.get("e1");

    expect(request).toHaveBeenCalledWith("/entries/e1");
  });

  it("создаёт запись с переданным или сгенерированным ключом идемпотентности", async () => {
    const body = { text: "т", occurredAt: "2026-10-18T12:00:00+03:00", timeZone: "Europe/Moscow" };

    await api.create(body, "draft-key");
    await api.create(body);

    expect(request.mock.calls[0][1]).toEqual({ method: "POST", body, headers: { "Idempotency-Key": "draft-key" } });
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "generated-key" });
  });

  it("правит и удаляет запись с If-Match по версии", async () => {
    await api.update("e1", 4, { text: "новый" });
    await api.remove("e1", 5);

    expect(request).toHaveBeenNthCalledWith(1, "/entries/e1", {
      method: "PATCH",
      body: { text: "новый" },
      headers: { "If-Match": '"v4"' },
    });
    expect(request).toHaveBeenNthCalledWith(2, "/entries/e1", { method: "DELETE", headers: { "If-Match": '"v5"' } });
  });
});
