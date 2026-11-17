import { randomUUID } from "expo-crypto";
import { useCallback, useEffect, useRef, useState } from "react";

import { clearDraft, loadDraft, saveDraft } from "@/features/entries/draft-store";

export type DraftStatus = "loading" | "empty" | "savedLocally" | "saveFailed";

const SAVE_DELAY_MS = 400;

export function useEntryDraft(userId: string) {
  const [text, setTextState] = useState("");
  const [status, setStatus] = useState<DraftStatus>("loading");
  const [initialKey] = useState(randomUUID);
  const idempotencyKey = useRef(initialKey);
  const latestText = useRef("");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const persist = useCallback(async () => {
    timer.current = null;
    const value = latestText.current;
    try {
      if (value.trim() === "") {
        await clearDraft(userId);
        setStatus("empty");
      } else {
        await saveDraft(userId, {
          text: value,
          idempotencyKey: idempotencyKey.current,
          updatedAt: new Date().toISOString(),
        });
        setStatus("savedLocally");
      }
    } catch {
      setStatus("saveFailed");
    }
  }, [userId]);

  useEffect(() => {
    let active = true;
    loadDraft(userId).then((draft) => {
      if (!active) {
        return;
      }
      if (draft) {
        latestText.current = draft.text;
        idempotencyKey.current = draft.idempotencyKey;
        setTextState(draft.text);
        setStatus("savedLocally");
      } else {
        setStatus("empty");
      }
    });
    return () => {
      active = false;
    };
  }, [userId]);

  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
        void persist();
      }
    },
    [persist],
  );

  const setText = useCallback(
    (value: string) => {
      latestText.current = value;
      setTextState(value);
      if (timer.current) {
        clearTimeout(timer.current);
      }
      timer.current = setTimeout(() => void persist(), SAVE_DELAY_MS);
    },
    [persist],
  );

  const discard = useCallback(async () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    latestText.current = "";
    idempotencyKey.current = randomUUID();
    setTextState("");
    setStatus("empty");
    await clearDraft(userId).catch(() => undefined);
  }, [userId]);

  return { text, setText, status, discard, getIdempotencyKey: () => idempotencyKey.current };
}
