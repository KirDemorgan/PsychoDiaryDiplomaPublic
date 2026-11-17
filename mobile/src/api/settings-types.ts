export interface UserSettings {
  aiEnabled: boolean;
  aiConsentVersion: string | null;
  aiConsentAcceptedAt: string | null;
  remindersEnabled: boolean;
  reminderRepeatMinutes: number | null;
  neutralNotificationText: boolean;
  theme: string | null;
  version: number;
}

export interface UpdateSettingsRequest {
  aiEnabled?: boolean;
  aiConsentVersion?: string;
  remindersEnabled?: boolean;
  reminderRepeatMinutes?: number | null;
  neutralNotificationText?: boolean;
  theme?: string;
}

export const AI_CONSENT_VERSION = "diary-ai-v1";
