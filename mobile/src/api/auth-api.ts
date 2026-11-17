import { httpClient, type HttpClient } from "./http-client";
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from "./types";

export function createAuthApi(client: HttpClient) {
  return {
    register: (body: RegisterRequest) =>
      client.request<AuthResponse>("/auth/register", { method: "POST", body, authenticated: false }),
    login: (body: LoginRequest) =>
      client.request<AuthResponse>("/auth/login", { method: "POST", body, authenticated: false }),
    refresh: (refreshToken: string) =>
      client.request<AuthResponse>("/auth/refresh", { method: "POST", body: { refreshToken }, authenticated: false }),
    logout: (refreshToken: string) =>
      client.request<void>("/auth/logout", { method: "POST", body: { refreshToken }, authenticated: false }),
    me: () => client.request<UserResponse>("/me"),
  };
}

export type AuthApi = ReturnType<typeof createAuthApi>;

export const authApi = createAuthApi(httpClient);
