import { createAccountApi } from "./account-api";
import { createExportsApi } from "./exports-api";
import type { HttpClient } from "./http-client";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));

describe("accountApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createAccountApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockClear());

  it("правит профиль с версией", async () => {
    await api.updateProfile(3, { displayName: null });

    expect(request).toHaveBeenCalledWith("/me", { method: "PATCH", body: { displayName: null }, headers: { "If-Match": '"v3"' } });
  });

  it("удаляет аккаунт с паролем и подтверждением", async () => {
    await api.deleteAccount("secret");

    expect(request).toHaveBeenCalledWith("/me", { method: "DELETE", body: { currentPassword: "secret", confirm: true } });
  });
});

describe("exportsApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const requestBinary = jest.fn().mockResolvedValue(new ArrayBuffer(0));
  const api = createExportsApi({ request, requestBinary } as unknown as HttpClient);

  it("создаёт экспорт с ключом идемпотентности и скачивает архив", async () => {
    await api.create();
    await api.create("fixed");
    await api.file("x1");

    expect(request.mock.calls[0][1].headers).toEqual({ "Idempotency-Key": "generated-key" });
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "fixed" });
    expect(requestBinary).toHaveBeenCalledWith("/data-exports/x1/file", { accept: "application/zip" });
  });
});
