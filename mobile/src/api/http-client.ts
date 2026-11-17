import { API_BASE_URL, REQUEST_TIMEOUT_MS } from "@/config/env";

import { ApiError, NETWORK_ERROR, TIMEOUT_ERROR, UNEXPECTED_RESPONSE } from "./errors";
import type { ProblemDetails } from "./types";

export interface SessionGateway {
  getAccessToken(): string | null;
  refreshSession(): Promise<boolean>;
}

export interface RequestOptions {
  accept?: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  authenticated?: boolean;
  signal?: AbortSignal;
}

type FetchLike = typeof fetch;

export class HttpClient {
  private gateway: SessionGateway | null = null;

  constructor(
    private readonly baseUrl: string = API_BASE_URL,
    private readonly fetchImpl: FetchLike = (...args) => fetch(...args),
    private readonly timeoutMs: number = REQUEST_TIMEOUT_MS,
  ) {}

  attachSession(gateway: SessionGateway): void {
    this.gateway = gateway;
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const authenticated = options.authenticated ?? true;
    const response = await this.send(path, options, authenticated);

    if (response.status === 401 && authenticated && this.gateway && (await this.gateway.refreshSession())) {
      return this.parse<T>(await this.send(path, options, authenticated));
    }
    return this.parse<T>(response);
  }

  async requestBinary(path: string, options: RequestOptions = {}): Promise<ArrayBuffer> {
    const authenticated = options.authenticated ?? true;
    let response = await this.send(path, { ...options, accept: options.accept ?? "application/octet-stream" }, authenticated);

    if (response.status === 401 && authenticated && this.gateway && (await this.gateway.refreshSession())) {
      response = await this.send(path, { ...options, accept: options.accept ?? "application/octet-stream" }, authenticated);
    }
    if (!response.ok) {
      throw ApiError.fromProblem(response.status, (await readJson(response)) as ProblemDetails | null);
    }
    return response.arrayBuffer();
  }

  private async send(path: string, options: RequestOptions, authenticated: boolean): Promise<Response> {
    const headers: Record<string, string> = { Accept: options.accept ?? "application/json", ...options.headers };
    const multipart = options.body instanceof FormData;
    if (options.body !== undefined && !multipart) {
      headers["Content-Type"] = "application/json";
    }
    const token = authenticated ? this.gateway?.getAccessToken() : null;
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const timeout = AbortSignal.timeout(this.timeoutMs);
    const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;

    try {
      return await this.fetchImpl(`${this.baseUrl}${path}`, {
        method: options.method ?? "GET",
        headers,
        body: options.body === undefined || multipart ? (options.body as FormData | undefined) : JSON.stringify(options.body),
        signal,
      });
    } catch (error) {
      if (timeout.aborted) {
        throw new ApiError(0, TIMEOUT_ERROR, "Сервер не ответил вовремя");
      }
      if (options.signal?.aborted) {
        throw error;
      }
      throw new ApiError(0, NETWORK_ERROR, "Нет связи с сервером");
    }
  }

  private async parse<T>(response: Response): Promise<T> {
    if (response.status === 204) {
      return undefined as T;
    }
    const payload = await readJson(response);
    if (!response.ok) {
      throw ApiError.fromProblem(response.status, payload as ProblemDetails | null);
    }
    if (payload === null) {
      throw new ApiError(response.status, UNEXPECTED_RESPONSE, "Сервер вернул неожиданный ответ");
    }
    return payload as T;
  }
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

export const httpClient = new HttpClient();
