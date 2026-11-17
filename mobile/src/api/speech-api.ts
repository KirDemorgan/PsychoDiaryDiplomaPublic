import { randomUUID } from "expo-crypto";
import { File } from "expo-file-system";

import { httpClient, type HttpClient } from "./http-client";
import { SPEECH_CONSENT_VERSION, type SpeechConsent, type Transcription } from "./speech-types";
import { formatEtag } from "./versions";

export function createSpeechApi(client: HttpClient) {
  return {
    consent: () => client.request<SpeechConsent>("/speech/consent"),

    updateConsent: (version: number, enabled: boolean) =>
      client.request<SpeechConsent>("/speech/consent", {
        method: "PUT",
        body: { enabled, consentVersion: enabled ? SPEECH_CONSENT_VERSION : null },
        headers: { "If-Match": formatEtag(version) },
      }),

    transcribe: (audioUri: string, idempotencyKey: string = randomUUID()) => {
      const form = new FormData();
      form.append("audio", new File(audioUri) as unknown as Blob);
      return client.request<Transcription>("/speech/transcriptions", {
        method: "POST",
        body: form,
        headers: { "Idempotency-Key": idempotencyKey },
      });
    },

    get: (id: string) => client.request<Transcription>(`/speech/transcriptions/${id}`),

    cancel: (id: string) => client.request<Transcription>(`/speech/transcriptions/${id}/cancel`, { method: "POST" }),

    remove: (id: string) => client.request<void>(`/speech/transcriptions/${id}`, { method: "DELETE" }),
  };
}

export type SpeechApi = ReturnType<typeof createSpeechApi>;

export const speechApi = createSpeechApi(httpClient);
