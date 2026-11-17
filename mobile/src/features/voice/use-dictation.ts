import {
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from "expo-audio";
import { randomUUID } from "expo-crypto";
import { useCallback, useEffect, useRef, useState } from "react";

import { describeError, isApiError } from "@/api/errors";
import { speechApi } from "@/api/speech-api";

import { MAX_RECORDING_SECONDS, WAV_RECORDING } from "./recording-options";
import { TranscriptionFailure, transcribeRecording } from "./transcribe";

export type DictationPhase = "idle" | "recording" | "processing";

export const MICROPHONE_DENIED = "Нет доступа к микрофону. Разрешите его в настройках iOS.";

function describeDictationError(failure: unknown): string {
  if (failure instanceof TranscriptionFailure) {
    return failure.message;
  }
  if (isApiError(failure) && failure.status === 503) {
    return "Сервис распознавания речи не настроен.";
  }
  return describeError(failure);
}

export function useDictation(onTranscript: (text: string) => void) {
  const recorder = useAudioRecorder(WAV_RECORDING);
  const state = useAudioRecorderState(recorder);
  const [phase, setPhase] = useState<DictationPhase>("idle");
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abort = useRef<AbortController | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  const clearTimer = () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  };

  const stop = useCallback(async () => {
    clearTimer();
    await recorder.stop();
    await setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
    const uri = recorder.uri;
    if (!uri) {
      setError("Запись не получилась. Попробуйте ещё раз.");
      setPhase("idle");
      return;
    }
    setPhase("processing");
    abort.current = new AbortController();
    try {
      onTranscript(
        await transcribeRecording(uri, idempotencyKey.current ?? randomUUID(), {
          api: speechApi,
          signal: abort.current.signal,
        }),
      );
    } catch (failure) {
      setError(describeDictationError(failure));
    } finally {
      abort.current = null;
      setPhase("idle");
    }
  }, [recorder, onTranscript]);

  const start = useCallback(async () => {
    setError(null);
    const permission = await requestRecordingPermissionsAsync();
    if (!permission.granted) {
      setError(MICROPHONE_DENIED);
      return;
    }
    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      recorder.record();
    } catch {
      setError("Не удалось начать запись. Попробуйте ещё раз.");
      return;
    }
    idempotencyKey.current = randomUUID();
    setPhase("recording");
    timer.current = setTimeout(() => void stop(), MAX_RECORDING_SECONDS * 1000);
  }, [recorder, stop]);

  const cancel = useCallback(() => abort.current?.abort(), []);

  useEffect(() => clearTimer, []);

  return {
    phase,
    error,
    seconds: Math.floor(state.durationMillis / 1000),
    start,
    stop,
    cancel,
  };
}
