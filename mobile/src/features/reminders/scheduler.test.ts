import * as Notifications from "expo-notifications";

import { cancelAllReminders, readPermission, replaceReminders, requestPermission } from "./scheduler";

jest.mock("expo-notifications", () => ({
  setNotificationHandler: jest.fn(),
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  cancelAllScheduledNotificationsAsync: jest.fn(),
  scheduleNotificationAsync: jest.fn(),
  SchedulableTriggerInputTypes: { DATE: "date" },
}));

const mocked = Notifications as jest.Mocked<typeof Notifications>;
const reminder = (id: string) => ({ id, at: new Date("2026-10-18T11:00:00Z"), title: "Напоминание", body: "Текст" });

const permissionStatus = (granted: boolean, canAskAgain: boolean) => ({ granted, canAskAgain }) as never;

describe("reminders scheduler", () => {
  beforeEach(() => {
    mocked.getPermissionsAsync.mockReset().mockResolvedValue(permissionStatus(true, true));
    mocked.cancelAllScheduledNotificationsAsync.mockReset().mockResolvedValue(undefined);
    mocked.scheduleNotificationAsync.mockReset().mockResolvedValue("id");
  });

  it("настраивает показ уведомлений при открытом приложении", () => {
    expect(mocked.setNotificationHandler).toHaveBeenCalledTimes(1);
  });

  it("описывает разрешение: выдано, можно спросить, запрещено", async () => {
    mocked.getPermissionsAsync.mockResolvedValueOnce(permissionStatus(true, false));
    mocked.getPermissionsAsync.mockResolvedValueOnce(permissionStatus(false, true));
    mocked.getPermissionsAsync.mockResolvedValueOnce(permissionStatus(false, false));

    await expect(readPermission()).resolves.toBe("granted");
    await expect(readPermission()).resolves.toBe("undetermined");
    await expect(readPermission()).resolves.toBe("denied");
  });

  it("запрашивает разрешение на оповещения и звук", async () => {
    mocked.requestPermissionsAsync.mockResolvedValue(permissionStatus(true, true));

    await expect(requestPermission()).resolves.toBe("granted");

    expect(mocked.requestPermissionsAsync).toHaveBeenCalledWith({ ios: { allowAlert: true, allowSound: true } });
  });

  it("сначала очищает прежние уведомления, затем планирует новые с идентификатором приёма", async () => {
    await replaceReminders([reminder("o1"), reminder("o2")]);

    expect(mocked.cancelAllScheduledNotificationsAsync).toHaveBeenCalledTimes(1);
    expect(mocked.scheduleNotificationAsync).toHaveBeenCalledTimes(2);
    expect(mocked.scheduleNotificationAsync).toHaveBeenCalledWith({
      identifier: "o1",
      content: { title: "Напоминание", body: "Текст" },
      trigger: { type: "date", date: new Date("2026-10-18T11:00:00Z") },
    });
  });

  it("без разрешения ничего не планирует, но прежние уведомления убирает", async () => {
    mocked.getPermissionsAsync.mockResolvedValue(permissionStatus(false, false));

    await replaceReminders([reminder("o1")]);

    expect(mocked.cancelAllScheduledNotificationsAsync).toHaveBeenCalled();
    expect(mocked.scheduleNotificationAsync).not.toHaveBeenCalled();
  });

  it("выполняет вызовы по очереди: побеждает последний набор без дублей", async () => {
    let releaseFirst: () => void = () => undefined;
    mocked.cancelAllScheduledNotificationsAsync.mockImplementationOnce(() => new Promise<void>((resolve) => (releaseFirst = resolve)));

    const first = replaceReminders([reminder("old")]);
    const second = replaceReminders([reminder("new")]);
    await new Promise((resolve) => setImmediate(resolve));
    releaseFirst();
    await Promise.all([first, second]);

    const scheduled = mocked.scheduleNotificationAsync.mock.calls.map((call) => call[0].identifier);
    expect(scheduled).toEqual(["old", "new"]);
    expect(mocked.cancelAllScheduledNotificationsAsync).toHaveBeenCalledTimes(2);
  });

  it("сбой одного вызова не блокирует следующий", async () => {
    mocked.cancelAllScheduledNotificationsAsync.mockRejectedValueOnce(new Error("native"));

    await expect(replaceReminders([reminder("o1")])).rejects.toThrow("native");
    await replaceReminders([reminder("o2")]);

    expect(mocked.scheduleNotificationAsync).toHaveBeenCalledWith(expect.objectContaining({ identifier: "o2" }));
  });

  it("полная отмена убирает все уведомления", async () => {
    await cancelAllReminders();

    expect(mocked.cancelAllScheduledNotificationsAsync).toHaveBeenCalled();
    expect(mocked.scheduleNotificationAsync).not.toHaveBeenCalled();
  });
});
