import { useEffect } from "react";

import { useOccurrences } from "@/hooks/use-medications";
import { useSettings } from "@/hooks/use-settings";
import { localDateIn } from "@/lib/dates";
import { deviceTimeZone } from "@/lib/time-zone";

import { planReminders } from "./plan";
import { cancelAllReminders, replaceReminders } from "./scheduler";

const HORIZON_DAYS = 6;
const DAY_MS = 24 * 60 * 60 * 1000;

export function useReminderSync(): void {
  const timeZone = deviceTimeZone();
  const now = new Date();
  const from = localDateIn(timeZone, now);
  const to = localDateIn(timeZone, new Date(now.getTime() + HORIZON_DAYS * DAY_MS));
  const settings = useSettings();
  const occurrences = useOccurrences(from, to, timeZone);

  const enabled = settings.data?.remindersEnabled;
  const neutralText = settings.data?.neutralNotificationText ?? true;
  const items = occurrences.data?.items;

  useEffect(() => {
    if (enabled === undefined) {
      return;
    }
    if (!enabled) {
      void cancelAllReminders();
      return;
    }
    if (items) {
      void replaceReminders(planReminders(items, { now: new Date(), neutralText }));
    }
  }, [enabled, neutralText, items]);
}
