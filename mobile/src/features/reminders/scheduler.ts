import * as Notifications from "expo-notifications";

import type { PlannedReminder } from "./plan";

export type ReminderPermission = "granted" | "denied" | "undetermined";

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

function toPermission(status: Notifications.NotificationPermissionsStatus): ReminderPermission {
  if (status.granted) {
    return "granted";
  }
  return status.canAskAgain ? "undetermined" : "denied";
}

export async function readPermission(): Promise<ReminderPermission> {
  return toPermission(await Notifications.getPermissionsAsync());
}

export async function requestPermission(): Promise<ReminderPermission> {
  return toPermission(await Notifications.requestPermissionsAsync({ ios: { allowAlert: true, allowSound: true } }));
}

let running: Promise<void> = Promise.resolve();

export function replaceReminders(reminders: readonly PlannedReminder[]): Promise<void> {
  running = running
    .catch(() => undefined)
    .then(async () => {
      await Notifications.cancelAllScheduledNotificationsAsync();
      if ((await readPermission()) !== "granted") {
        return;
      }
      for (const reminder of reminders) {
        await Notifications.scheduleNotificationAsync({
          identifier: reminder.id,
          content: { title: reminder.title, body: reminder.body },
          trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: reminder.at },
        });
      }
    });
  return running;
}

export function cancelAllReminders(): Promise<void> {
  return replaceReminders([]);
}
