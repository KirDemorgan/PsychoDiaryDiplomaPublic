import { authApi, type AuthApi } from "@/api/auth-api";
import { isApiError } from "@/api/errors";
import { httpClient, type SessionGateway } from "@/api/http-client";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/api/types";

import { useSessionStore } from "./session-store";
import { secureTokenStorage, type TokenStorage } from "./token-storage";

export class SessionService implements SessionGateway {
  private accessToken: string | null = null;
  private pendingRefresh: Promise<boolean> | null = null;

  constructor(
    private readonly api: AuthApi,
    private readonly storage: TokenStorage,
  ) {}

  getAccessToken(): string | null {
    return this.accessToken;
  }

  async restore(): Promise<void> {
    const stored = await this.storage.readRefreshToken();
    if (!stored || !(await this.refreshSession())) {
      this.accessToken = null;
      useSessionStore.getState().setSignedOut();
    }
  }

  async signIn(credentials: LoginRequest): Promise<void> {
    await this.establish(await this.api.login(credentials));
  }

  async signUp(request: RegisterRequest): Promise<void> {
    await this.establish(await this.api.register(request));
  }

  async signOut(): Promise<void> {
    const refreshToken = await this.storage.readRefreshToken();
    if (refreshToken) {
      await this.api.logout(refreshToken).catch(() => undefined);
    }
    await this.endSession();
  }

  refreshSession(): Promise<boolean> {
    this.pendingRefresh ??= this.performRefresh().finally(() => {
      this.pendingRefresh = null;
    });
    return this.pendingRefresh;
  }

  private async performRefresh(): Promise<boolean> {
    const refreshToken = await this.storage.readRefreshToken();
    if (!refreshToken) {
      return false;
    }
    try {
      await this.establish(await this.api.refresh(refreshToken));
      return true;
    } catch (error) {
      if (isApiError(error) && error.isUnauthorized) {
        await this.endSession();
      }
      return false;
    }
  }

  private async establish(auth: AuthResponse): Promise<void> {
    await this.storage.saveRefreshToken(auth.refreshToken);
    this.accessToken = auth.accessToken;
    useSessionStore.getState().setSignedIn(auth.user);
  }

  private async endSession(): Promise<void> {
    this.accessToken = null;
    await this.storage.clear();
    useSessionStore.getState().setSignedOut();
  }
}

export const sessionService = new SessionService(authApi, secureTokenStorage);

httpClient.attachSession(sessionService);
