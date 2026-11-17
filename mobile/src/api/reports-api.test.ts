import type { HttpClient } from "./http-client";
import { createReportsApi } from "./reports-api";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("reportsApi", () => {
  const request = jest.fn();
  const requestBinary = jest.fn();
  const api = createReportsApi({ request, requestBinary } as unknown as HttpClient);

  beforeEach(() => {
    request.mockReset().mockResolvedValue({});
    requestBinary.mockReset().mockResolvedValue(new ArrayBuffer(0));
  });

  it("создаёт отчёт с ключом идемпотентности", async () => {
    const body = {
      from: "2026-10-01",
      to: "2026-10-18",
      timeZone: "UTC",
      sections: ["RATINGS"],
      entrySelection: "NONE",
      medicationSelection: "NONE",
    } as const;

    await api.create({ ...body, sections: [...body.sections] }, "k");
    await api.create({ ...body, sections: [...body.sections] });

    expect(request.mock.calls[0][1].headers).toEqual({ "Idempotency-Key": "k" });
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "generated-key" });
    expect(request.mock.calls[0]).toEqual(["/reports", expect.objectContaining({ method: "POST" })]);
  });

  it("возвращает элементы списка", async () => {
    request.mockResolvedValue({ items: [{ id: "r1" }] });

    await expect(api.list()).resolves.toEqual([{ id: "r1" }]);
    expect(request).toHaveBeenCalledWith("/reports?size=50");
  });

  it("читает, отменяет и удаляет отчёт", async () => {
    await api.get("r1");
    await api.cancel("r1");
    await api.remove("r1", 3);

    expect(request).toHaveBeenNthCalledWith(1, "/reports/r1");
    expect(request).toHaveBeenNthCalledWith(2, "/reports/r1/cancel", {
      method: "POST",
      headers: { "Idempotency-Key": "generated-key" },
    });
    expect(request).toHaveBeenNthCalledWith(3, "/reports/r1", { method: "DELETE", headers: { "If-Match": '"v3"' } });
  });

  it("скачивает PDF с выбором AI-обзора", async () => {
    await api.pdf("r1", false);
    await api.pdf("r1", true);

    expect(requestBinary).toHaveBeenNthCalledWith(1, "/reports/r1/pdf?includeAiOverview=false", { accept: "application/pdf" });
    expect(requestBinary).toHaveBeenNthCalledWith(2, "/reports/r1/pdf?includeAiOverview=true", { accept: "application/pdf" });
  });
});
