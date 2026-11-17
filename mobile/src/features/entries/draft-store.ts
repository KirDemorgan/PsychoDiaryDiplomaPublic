import AsyncStorage from "@react-native-async-storage/async-storage";

export interface EntryDraft {
  text: string;
  idempotencyKey: string;
  updatedAt: string;
}

const keyFor = (userId: string) => `diary.entry-draft.${userId}`;

export async function loadDraft(userId: string): Promise<EntryDraft | null> {
  try {
    const raw = await AsyncStorage.getItem(keyFor(userId));
    if (!raw) {
      return null;
    }
    const value = JSON.parse(raw) as Partial<EntryDraft>;
    return typeof value.text === "string" && typeof value.idempotencyKey === "string"
      ? { text: value.text, idempotencyKey: value.idempotencyKey, updatedAt: value.updatedAt ?? "" }
      : null;
  } catch {
    return null;
  }
}

export async function saveDraft(userId: string, draft: EntryDraft): Promise<void> {
  await AsyncStorage.setItem(keyFor(userId), JSON.stringify(draft));
}

export async function clearDraft(userId: string): Promise<void> {
  await AsyncStorage.removeItem(keyFor(userId));
}
