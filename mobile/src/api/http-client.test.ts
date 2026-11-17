import { ApiError } from "./errors";
import { HttpClient, type SessionGateway } from "./http-client";

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function createClient(fetchImpl: jest.Mock, gateway?: SessionGateway) {
  const client = new HttpClient("http://api.test/api/v1", fetchImpl as unknown as typeof fetch, 1000);
  if (gateway) {
    client.attachSession(gateway);
  }
  return client;
}

function gatewayWith(token: string | null, refresh = jest.fn().mockResolvedValue(false)): SessionGateway {
  return { getAccessToken: () => token, refreshSession: refresh };
}

describe("HttpClient", () => {
  it("отправляет JSON и токен, возвращает разобранный ответ", async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    const client = createClient(fetchMock, gatewayWith("access"));

    const result = await client.request<{ ok: boolean }>("/ping", { method: "POST", body: { a: 1 } });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/v1/ping");
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ a: 1 }));
    expect(init.headers.Authorization).toBe("Bearer access");
    expect(init.headers["Content-Type"]).toBe("application/json");
  });

  it("не добавляет токен для неавторизованных запросов", async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(200, {}));
    const client = createClient(fetchMock, gatewayWith("access"));

    await client.request("/auth/login", { authenticated: false });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it("возвращает undefined для 204", async () => {
    const client = createClient(jest.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(client.request("/x", { method: "DELETE" })).resolves.toBeUndefined();
  });

  it("превращает problem+json в ApiError", async () => {
    const fetchMock = jest
      .fn()
      .mockResolvedValue(jsonResponse(400, { code: "VALIDATION_ERROR", detail: "Проверьте поля запроса" }));
    const client = createClient(fetchMock);

    await expect(client.request("/x")).rejects.toMatchObject({ status: 400, code: "VALIDATION_ERROR" });
  });

  it("сообщает о неожиданном теле успешного ответа", async () => {
    const client = createClient(jest.fn().mockResolvedValue(new Response("not json", { status: 200 })));

    await expect(client.request("/x")).rejects.toMatchObject({ code: "UNEXPECTED_RESPONSE" });
  });

  it("повторяет запрос один раз после успешного обновления сессии", async () => {
    const fetchMock = jest
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "INVALID_CREDENTIALS" }))
      .mockResolvedValueOnce(jsonResponse(200, { id: 1 }));
    const refresh = jest.fn().mockResolvedValue(true);
    const client = createClient(fetchMock, gatewayWith("access", refresh));

    await expect(client.request("/me")).resolves.toEqual({ id: 1 });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("возвращает 401, если обновить сессию не удалось", async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(401, { code: "INVALID_CREDENTIALS" }));
    const client = createClient(fetchMock, gatewayWith("access"));

    await expect(client.request("/me")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("не пытается обновлять сессию для неавторизованных запросов", async () => {
    const refresh = jest.fn().mockResolvedValue(true);
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(401, { code: "INVALID_CREDENTIALS" }));
    const client = createClient(fetchMock, gatewayWith(null, refresh));

    await expect(client.request("/auth/login", { authenticated: false })).rejects.toBeInstanceOf(ApiError);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("превращает сбой сети в NETWORK_ERROR", async () => {
    const client = createClient(jest.fn().mockRejectedValue(new TypeError("Network request failed")));

    await expect(client.request("/x")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  it("превращает истёкший таймаут в TIMEOUT_ERROR", async () => {
    const fetchMock = jest.fn((_url: string, init: RequestInit) => {
      return new Promise<Response>((_resolve, reject) => {
        init.signal?.addEventListener("abort", () => reject(new Error("aborted")));
      });
    });
    const client = new HttpClient("http://api.test", fetchMock as unknown as typeof fetch, 10);

    await expect(client.request("/slow")).rejects.toMatchObject({ code: "TIMEOUT_ERROR" });
  });

  it("пробрасывает отмену, инициированную вызывающим кодом", async () => {
    const controller = new AbortController();
    const abort = new Error("cancelled");
    const fetchMock = jest.fn().mockImplementation(() => {
      controller.abort();
      return Promise.reject(abort);
    });
    const client = createClient(fetchMock);

    await expect(client.request("/x", { signal: controller.signal })).rejects.toBe(abort);
  });
});

describe("HttpClient.requestBinary", () => {
  it("возвращает байты ответа и просит указанный тип", async () => {
    const bytes = new Uint8Array([37, 80, 68, 70]).buffer;
    const fetchMock = jest.fn().mockResolvedValue(new Response(bytes, { status: 200 }));
    const client = createClient(fetchMock, gatewayWith("access"));

    const result = await client.requestBinary("/reports/r1/pdf", { accept: "application/pdf" });

    expect(new Uint8Array(result)).toEqual(new Uint8Array([37, 80, 68, 70]));
    expect(fetchMock.mock.calls[0][1].headers).toMatchObject({ Accept: "application/pdf", Authorization: "Bearer access" });
  });

  it("без указанного типа просит любые байты", async () => {
    const fetchMock = jest.fn().mockResolvedValue(new Response(new ArrayBuffer(1), { status: 200 }));

    await createClient(fetchMock).requestBinary("/x");

    expect(fetchMock.mock.calls[0][1].headers.Accept).toBe("application/octet-stream");
  });

  it("повторяет запрос после обновления сессии", async () => {
    const fetchMock = jest
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" }))
      .mockResolvedValueOnce(new Response(new Uint8Array([1]).buffer, { status: 200 }));
    const client = createClient(fetchMock, gatewayWith("access", jest.fn().mockResolvedValue(true)));

    await expect(client.requestBinary("/x")).resolves.toBeInstanceOf(ArrayBuffer);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("превращает ошибку сервера в ApiError с кодом", async () => {
    const client = createClient(jest.fn().mockResolvedValue(jsonResponse(409, { code: "REPORT_STALE", detail: "Источники изменились" })));

    await expect(client.requestBinary("/reports/r1/pdf")).rejects.toMatchObject({ status: 409, code: "REPORT_STALE" });
  });

  it("при ошибке без тела использует код статуса", async () => {
    const client = createClient(jest.fn().mockResolvedValue(new Response("not json", { status: 502 })));

    await expect(client.requestBinary("/x")).rejects.toMatchObject({ code: "HTTP_502" });
  });
});

describe("HttpClient multipart", () => {
  it("отправляет FormData как есть, не задавая Content-Type", async () => {
    const fetchMock = jest.fn().mockResolvedValue(jsonResponse(202, { id: "t1" }));
    const form = new FormData();
    form.append("a", "b");

    await createClient(fetchMock).request("/speech/transcriptions", { method: "POST", body: form });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBe(form);
    expect(init.headers["Content-Type"]).toBeUndefined();
  });
});
