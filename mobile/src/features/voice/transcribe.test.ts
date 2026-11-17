import type { Transcription } from "@/api/speech-types";

import { MAX_WAIT_MS, POLL_INTERVAL_MS, TranscriptionFailure, transcribeRecording } from "./transcribe";

const job = (status: Transcription["status"], overrides: Partial<Transcription> = {}): Transcription => ({
  id: "t1",
  status,
  transcript: null,
  modelId: null,
  durationMillis: 1000,
  attempt: 1,
  expiresAt: null,
  finishedAt: null,
  errorCode: null,
  ...overrides,
});

const makeApi = (first: Transcription, ...next: Transcription[]) => ({
  transcribe: jest.fn().mockResolvedValue(first),
  get: jest.fn().mockImplementation(() => Promise.resolve(next.shift() ?? first)),
  remove: jest.fn().mockResolvedValue(undefined),
});

describe("transcribeRecording", () => {
  it("опрашивает задачу до готовности, возвращает текст и удаляет расшифровку на сервере", async () => {
    const api = makeApi(job("QUEUED"), job("RUNNING"), job("SUCCEEDED", { transcript: "Привет" }));
    const wait = jest.fn().mockResolvedValue(undefined);

    const text = await transcribeRecording("file:///a.wav", "key", { api, wait });

    expect(text).toBe("Привет");
    expect(api.transcribe).toHaveBeenCalledWith("file:///a.wav", "key");
    expect(wait).toHaveBeenCalledTimes(2);
    expect(wait).toHaveBeenCalledWith(POLL_INTERVAL_MS);
    expect(api.remove).toHaveBeenCalledWith("t1");
  });

  it("сразу возвращает результат, если распознавание уже готово", async () => {
    const api = makeApi(job("SUCCEEDED", { transcript: "Готово" }));
    const wait = jest.fn();

    await expect(transcribeRecording("u", "k", { api, wait })).resolves.toBe("Готово");
    expect(wait).not.toHaveBeenCalled();
  });

  it("сбой удаления расшифровки не мешает результату", async () => {
    const api = makeApi(job("SUCCEEDED", { transcript: "Текст" }));
    api.remove.mockRejectedValue(new Error("network"));

    await expect(transcribeRecording("u", "k", { api })).resolves.toBe("Текст");
  });

  it.each([
    ["FAILED", null, /Не удалось распознать речь/],
    ["FAILED", "ASR_UNAVAILABLE", /недоступен/],
    ["CANCELLED", null, /отменено/],
    ["EXPIRED", null, /Время хранения/],
  ] as const)("статус %s с кодом %s превращается в понятную ошибку", async (status, code, message) => {
    const api = makeApi(job(status, { errorCode: code }));

    const failure = await transcribeRecording("u", "k", { api }).catch((error) => error);

    expect(failure).toBeInstanceOf(TranscriptionFailure);
    expect(failure.message).toMatch(message);
    expect(failure.code).toBe(code);
    expect(api.remove).not.toHaveBeenCalled();
  });

  it("успех без текста считается ошибкой", async () => {
    const api = makeApi(job("SUCCEEDED", { transcript: "" }));

    await expect(transcribeRecording("u", "k", { api })).rejects.toBeInstanceOf(TranscriptionFailure);
  });

  it("прекращает ожидание по сигналу отмены", async () => {
    const controller = new AbortController();
    const api = makeApi(job("RUNNING"));
    const wait = jest.fn().mockImplementation(async () => controller.abort());

    await expect(transcribeRecording("u", "k", { api, wait, signal: controller.signal })).rejects.toThrow("отменено");
  });

  it("не ждёт дольше лимита", async () => {
    let time = 0;
    const api = makeApi(job("RUNNING"));
    const wait = jest.fn().mockImplementation(async () => {
      time += MAX_WAIT_MS;
    });

    await expect(transcribeRecording("u", "k", { api, wait, now: () => time })).rejects.toBeInstanceOf(TranscriptionFailure);
  });
});
