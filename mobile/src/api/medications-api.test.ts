import type { HttpClient } from "./http-client";
import { createMedicationsApi } from "./medications-api";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("medicationsApi", () => {
  const request = jest.fn();
  const api = createMedicationsApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockReset().mockResolvedValue({}));

  it("запрашивает активные и архивные препараты", async () => {
    request.mockResolvedValue({ items: [{ id: "m1" }] });

    await expect(api.list()).resolves.toEqual([{ id: "m1" }]);
    await api.list(true);

    expect(request).toHaveBeenNthCalledWith(1, "/medications?archived=false&size=100");
    expect(request).toHaveBeenNthCalledWith(2, "/medications?archived=true&size=100");
  });

  it("читает препарат и историю схем", async () => {
    await api.get("m1");
    await api.scheduleHistory("m1");

    expect(request).toHaveBeenNthCalledWith(1, "/medications/m1");
    expect(request).toHaveBeenNthCalledWith(2, "/medications/m1/schedule-versions");
  });

  it("создаёт и правит препарат", async () => {
    await api.create({ name: "Препарат", form: "таблетки" }, "k1");
    await api.create({ name: "Другой" });
    await api.update("m1", 2, { name: "Новое" });

    expect(request.mock.calls[0]).toEqual([
      "/medications",
      { method: "POST", body: { name: "Препарат", form: "таблетки" }, headers: { "Idempotency-Key": "k1" } },
    ]);
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "generated-key" });
    expect(request.mock.calls[2]).toEqual([
      "/medications/m1",
      { method: "PATCH", body: { name: "Новое" }, headers: { "If-Match": '"v2"' } },
    ]);
  });

  it("создаёт версию схемы с версией препарата и ключом", async () => {
    const body = { effectiveFrom: "2026-10-18T10:00:00+05:00", treatmentEndsOn: null, timeZone: "Asia/Yekaterinburg", slots: [] };

    await api.createSchedule("m1", 3, body);

    expect(request).toHaveBeenCalledWith("/medications/m1/schedule-versions", {
      method: "POST",
      body,
      headers: { "If-Match": '"v3"', "Idempotency-Key": "generated-key" },
    });
  });

  it("архивирует и удаляет препарат", async () => {
    await api.archive("m1", 4, "k");
    await api.remove("m1", 5);

    expect(request).toHaveBeenNthCalledWith(1, "/medications/m1/archive", {
      method: "POST",
      headers: { "If-Match": '"v4"', "Idempotency-Key": "k" },
    });
    expect(request).toHaveBeenNthCalledWith(2, "/medications/m1", { method: "DELETE", headers: { "If-Match": '"v5"' } });
  });

  it("запрашивает плановые приёмы за период в часовом поясе", async () => {
    await api.occurrences("2026-10-18", "2026-10-19", "Asia/Yekaterinburg");

    expect(request).toHaveBeenCalledWith(
      "/medication-occurrences?from=2026-10-18&to=2026-10-19&timeZone=Asia%2FYekaterinburg",
    );
  });

  it("отмечает приём и снимает отметку", async () => {
    const body = { kind: "PLANNED", occurrenceId: "o1", status: "SKIPPED" } as const;

    await api.createIntake(body, "k");
    await api.removeIntake("i1", 6);

    expect(request).toHaveBeenNthCalledWith(1, "/intakes", { method: "POST", body, headers: { "Idempotency-Key": "k" } });
    expect(request).toHaveBeenNthCalledWith(2, "/intakes/i1", { method: "DELETE", headers: { "If-Match": '"v6"' } });
  });
});
