import { createAnalysisApi } from "./analysis-api";
import type { HttpClient } from "./http-client";
import { createSettingsApi } from "./settings-api";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("analysisApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createAnalysisApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockClear());

  it("запускает анализ редакции с ключом идемпотентности", async () => {
    await api.start("e1", 2);
    await api.start("e1", 2, "fixed");

    expect(request.mock.calls[0]).toEqual([
      "/entries/e1/analyses",
      { method: "POST", body: { revision: 2 }, headers: { "Idempotency-Key": "generated-key" } },
    ]);
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "fixed" });
  });

  it("читает и отменяет анализ", async () => {
    await api.get("e1", "a1");
    await api.cancel("e1", "a1");

    expect(request).toHaveBeenNthCalledWith(1, "/entries/e1/analyses/a1");
    expect(request).toHaveBeenNthCalledWith(2, "/entries/e1/analyses/a1/cancel", {
      method: "POST",
      headers: { "Idempotency-Key": "generated-key" },
    });
  });

  it("читает словарь тегов", async () => {
    await api.definitions();

    expect(request).toHaveBeenCalledWith("/tag-definitions");
  });

  it("отправляет подтверждение тегов с версией записи и ключом", async () => {
    const body = { revision: 1, complete: true, decisions: [], manualTags: [] };

    await api.submitReview("e1", 5, body);

    expect(request).toHaveBeenCalledWith("/entries/e1/tag-review", {
      method: "PUT",
      body,
      headers: { "Idempotency-Key": "generated-key", "If-Match": '"v5"' },
    });
  });
});

describe("settingsApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createSettingsApi({ request } as unknown as HttpClient);

  it("читает и обновляет настройки с If-Match", async () => {
    await api.get();
    await api.update(4, { aiEnabled: false });

    expect(request).toHaveBeenNthCalledWith(1, "/me/settings");
    expect(request).toHaveBeenNthCalledWith(2, "/me/settings", {
      method: "PATCH",
      body: { aiEnabled: false },
      headers: { "If-Match": '"v4"' },
    });
  });
});
