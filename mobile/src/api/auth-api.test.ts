import { createAuthApi } from "./auth-api";
import type { HttpClient } from "./http-client";

describe("authApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createAuthApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockClear());

  it.each([
    ["register", () => api.register({ email: "a@b.co", password: "p".repeat(12), timeZone: "Europe/Moscow" }), "/auth/register"],
    ["login", () => api.login({ email: "a@b.co", password: "secret" }), "/auth/login"],
    ["refresh", () => api.refresh("r".repeat(43)), "/auth/refresh"],
    ["logout", () => api.logout("r".repeat(43)), "/auth/logout"],
  ])("%s идёт без авторизации методом POST", async (_name, call, path) => {
    await call();

    expect(request).toHaveBeenCalledWith(path, expect.objectContaining({ method: "POST", authenticated: false }));
  });

  it("me запрашивает профиль с авторизацией", async () => {
    await api.me();

    expect(request).toHaveBeenCalledWith("/me");
  });
});
