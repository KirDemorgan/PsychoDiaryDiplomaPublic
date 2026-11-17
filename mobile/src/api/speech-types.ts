export const SPEECH_CONSENT_VERSION = "diary-speech-v1";

export type TranscriptionStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "EXPIRED";

export interface Transcription {
  id: string;
  status: TranscriptionStatus;
  transcript: string | null;
  modelId: string | null;
  durationMillis: number;
  attempt: number;
  expiresAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
}

export interface SpeechConsent {
  version: number;
  enabled: boolean;
  consentVersion: string | null;
  acceptedAt: string | null;
  currentVersion: string;
}
