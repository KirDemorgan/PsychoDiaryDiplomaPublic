import { File } from "expo-file-system";

import type { HttpClient } from "./http-client";
import { createSpeechApi } from "./speech-api";

jest.mock("expo-crypto", () => ({ randomUUID: () => "generated-key" }));
jest.mock("expo-file-system", () => ({ File: jest.fn((uri: string) => ({ uri, name: "recording.wav", bytes: jest.fn() })) }));

describe("speechApi", () => {
  const request = jest.fn();
  const api = createSpeechApi({ request } as unknown as HttpClient);

  beforeEach(() => request.mockReset().mockResolvedValue({}));

  it("читает согласие и обновляет его с версией", async () => {
    await api.consent();
    await api.updateConsent(2, true);
    await api.updateConsent(3, false);

    expect(request).toHaveBeenNthCalledWith(1, "/speech/consent");
    expect(request).toHaveBeenNthCalledWith(2, "/speech/consent", {
      method: "PUT",
      body: { enabled: true, consentVersion: "diary-speech-v1" },
      headers: { "If-Match": '"v2"' },
    });
    expect(request.mock.calls[2][1].body).toEqual({ enabled: false, consentVersion: null });
  });

  it("загружает WAV как multipart-поле audio с ключом идемпотентности", async () => {
    await api.transcribe("file:///cache/dictation.wav", "fixed");
    await api.transcribe("file:///cache/dictation.wav");

    const [path, options] = request.mock.calls[0];
    expect(path).toBe("/speech/transcriptions");
    expect(options.method).toBe("POST");
    expect(options.headers).toEqual({ "Idempotency-Key": "fixed" });
    expect(options.body).toBeInstanceOf(FormData);
    expect(File).toHaveBeenCalledWith("file:///cache/dictation.wav");
    expect(request.mock.calls[1][1].headers).toEqual({ "Idempotency-Key": "generated-key" });
  });

  it("читает, отменяет и удаляет расшифровку", async () => {
    await api.get("t1");
    await api.cancel("t1");
    await api.remove("t1");

    expect(request).toHaveBeenNthCalledWith(1, "/speech/transcriptions/t1");
    expect(request).toHaveBeenNthCalledWith(2, "/speech/transcriptions/t1/cancel", { method: "POST" });
    expect(request).toHaveBeenNthCalledWith(3, "/speech/transcriptions/t1", { method: "DELETE" });
  });
});
