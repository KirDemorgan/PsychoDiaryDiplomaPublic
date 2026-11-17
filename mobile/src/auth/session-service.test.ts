import { ApiError, NETWORK_ERROR } from "@/api/errors";
import type { AuthApi } from "@/api/auth-api";
import type { AuthResponse } from "@/api/types";

import { SessionService } from "./session-service";
import { useSessionStore } from "./session-store";
import type { TokenStorage } from "./token-storage";

jest.mock("./token-storage", () => ({ secureTokenStorage: {} }));

const authResponse: AuthResponse = {
  user: { id: "u1", email: "a@b.co", displayName: "Анна", timeZone: "Europe/Moscow", version: 0, dataRevision: 0 },
  accessToken: "access-1",
  refreshToken: "refresh-1",
  tokenType: "Bearer",
  expiresIn: 900,
};

function createStorage(initial: string | null = null): jest.Mocked<TokenStorage> {
  let token = initial;
  return {
    readRefreshToken: jest.fn(async () => token),
    saveRefreshToken: jest.fn(async (value: string) => {
      token = value;
    }),
    clear: jest.fn(async () => {
      token = null;
    }),
  };
}

function createApi(overrides: Partial<Record<keyof AuthApi, jest.Mock>> = {}) {
  return {
    register: jest.fn().mockResolvedValue(authResponse),
    login: jest.fn().mockResolvedValue(authResponse),
    refresh: jest.fn().mockResolvedValue(authResponse),
    logout: jest.fn().mockResolvedValue(undefined),
    me: jest.fn(),
    ...overrides,
  };
}

describe("SessionService", () => {
  beforeEach(() => {
    useSessionStore.setState({ status: "restoring", user: null });
  });

  it("вход сохраняет refresh-токен, держит access в памяти и обновляет стор", async () => {
    const storage = createStorage();
    const service = new SessionService(createApi() as unknown as AuthApi, storage);

    await service.signIn({ email: "a@b.co", password: "secret" });

    expect(storage.saveRefreshToken).toHaveBeenCalledWith("refresh-1");
    expect(service.getAccessToken()).toBe("access-1");
    expect(useSessionStore.getState()).toMatchObject({ status: "signedIn", user: { email: "a@b.co" } });
  });

  it("регистрация устанавливает сессию", async () => {
    const api = createApi();
    const service = new SessionService(api as unknown as AuthApi, createStorage());

    await service.signUp({ email: "a@b.co", password: "p".repeat(12), timeZone: "Europe/Moscow" });

    expect(api.register).toHaveBeenCalled();
    expect(useSessionStore.getState().status).toBe("signedIn");
  });

  it("ошибка входа не меняет состояние", async () => {
    const api = createApi({ login: jest.fn().mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS", "x")) });
    const service = new SessionService(api as unknown as AuthApi, createStorage());

    await expect(service.signIn({ email: "a@b.co", password: "bad" })).rejects.toBeInstanceOf(ApiError);
    expect(useSessionStore.getState().status).toBe("restoring");
  });

  it("restore без сохранённого токена переводит в signedOut", async () => {
    const service = new SessionService(createApi() as unknown as AuthApi, createStorage());

    await service.restore();

    expect(useSessionStore.getState().status).toBe("signedOut");
  });

  it("restore с токеном обновляет сессию", async () => {
    const api = createApi();
    const service = new SessionService(api as unknown as AuthApi, createStorage("stored"));

    await service.restore();

    expect(api.refresh).toHaveBeenCalledWith("stored");
    expect(useSessionStore.getState().status).toBe("signedIn");
  });

  it("отклонённый refresh (401) очищает хранилище и завершает сессию", async () => {
    const api = createApi({ refresh: jest.fn().mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS", "x")) });
    const storage = createStorage("stale");
    const service = new SessionService(api as unknown as AuthApi, storage);

    await service.restore();

    expect(storage.clear).toHaveBeenCalled();
    expect(useSessionStore.getState().status).toBe("signedOut");
  });

  it("при недоступном сервере токен сохраняется, а сессия не обновляется", async () => {
    const api = createApi({ refresh: jest.fn().mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x")) });
    const storage = createStorage("kept");
    const service = new SessionService(api as unknown as AuthApi, storage);

    await expect(service.refreshSession()).resolves.toBe(false);
    expect(storage.clear).not.toHaveBeenCalled();
  });

  it("лимит запросов (429) не разрывает сессию", async () => {
    const api = createApi({ refresh: jest.fn().mockRejectedValue(new ApiError(429, "TOO_MANY", "x")) });
    const storage = createStorage("kept");
    const service = new SessionService(api as unknown as AuthApi, storage);

    await service.refreshSession();

    expect(storage.clear).not.toHaveBeenCalled();
  });

  it("параллельные обновления сворачиваются в один запрос", async () => {
    const api = createApi();
    const service = new SessionService(api as unknown as AuthApi, createStorage("stored"));

    const results = await Promise.all([service.refreshSession(), service.refreshSession(), service.refreshSession()]);

    expect(results).toEqual([true, true, true]);
    expect(api.refresh).toHaveBeenCalledTimes(1);
  });

  it("refresh без токена возвращает false", async () => {
    const service = new SessionService(createApi() as unknown as AuthApi, createStorage());

    await expect(service.refreshSession()).resolves.toBe(false);
  });

  it("выход отзывает сессию на сервере и очищает локальные данные", async () => {
    const api = createApi();
    const storage = createStorage("stored");
    const service = new SessionService(api as unknown as AuthApi, storage);
    await service.signIn({ email: "a@b.co", password: "secret" });

    await service.signOut();

    expect(api.logout).toHaveBeenCalledWith("refresh-1");
    expect(storage.clear).toHaveBeenCalled();
    expect(service.getAccessToken()).toBeNull();
    expect(useSessionStore.getState()).toMatchObject({ status: "signedOut", user: null });
  });

  it("выход завершается локально, даже если сервер недоступен", async () => {
    const api = createApi({ logout: jest.fn().mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x")) });
    const service = new SessionService(api as unknown as AuthApi, createStorage("stored"));

    await service.signOut();

    expect(useSessionStore.getState().status).toBe("signedOut");
  });

  it("выход без токена не обращается к серверу", async () => {
    const api = createApi();
    const service = new SessionService(api as unknown as AuthApi, createStorage());

    await service.signOut();

    expect(api.logout).not.toHaveBeenCalled();
  });
});
