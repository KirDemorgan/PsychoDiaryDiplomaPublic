import type { SpeechApi } from "@/api/speech-api";
import type { Transcription } from "@/api/speech-types";

export const POLL_INTERVAL_MS = 1500;
export const MAX_WAIT_MS = 120_000;

export const ERROR_MESSAGES: Record<string, string> = {
  ASR_UNAVAILABLE: "Сервис распознавания речи сейчас недоступен.",
};

export class TranscriptionFailure extends Error {
  readonly code: string | null;

  constructor(status: Transcription["status"], code: string | null) {
    super(describeFailure(status, code));
    this.name = "TranscriptionFailure";
    this.code = code;
  }
}

function describeFailure(status: Transcription["status"], code: string | null): string {
  if (status === "CANCELLED") {
    return "Распознавание отменено.";
  }
  if (status === "EXPIRED") {
    return "Время хранения записи истекло. Запишите ещё раз.";
  }
  return (code && ERROR_MESSAGES[code]) || "Не удалось распознать речь. Запишите ещё раз или введите текст.";
}

export interface TranscribeDeps {
  api: Pick<SpeechApi, "transcribe" | "get" | "remove">;
  wait?: (ms: number) => Promise<void>;
  now?: () => number;
  signal?: AbortSignal;
}

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export async function transcribeRecording(audioUri: string, idempotencyKey: string, deps: TranscribeDeps): Promise<string> {
  const { api, wait = delay, now = Date.now, signal } = deps;
  let current = await api.transcribe(audioUri, idempotencyKey);
  const deadline = now() + MAX_WAIT_MS;

  while (current.status === "QUEUED" || current.status === "RUNNING") {
    if (signal?.aborted) {
      throw new TranscriptionFailure("CANCELLED", null);
    }
    if (now() > deadline) {
      throw new TranscriptionFailure("FAILED", null);
    }
    await wait(POLL_INTERVAL_MS);
    current = await api.get(current.id);
  }

  if (current.status !== "SUCCEEDED" || !current.transcript) {
    throw new TranscriptionFailure(current.status, current.errorCode);
  }

  await api.remove(current.id).catch(() => undefined);
  return current.transcript;
}
