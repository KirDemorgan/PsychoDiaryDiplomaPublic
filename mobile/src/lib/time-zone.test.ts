import { getCalendars } from "expo-localization";

import { deviceTimeZone } from "./time-zone";

jest.mock("expo-localization", () => ({ getCalendars: jest.fn() }));

describe("deviceTimeZone", () => {
  it("возвращает зону устройства", () => {
    (getCalendars as jest.Mock).mockReturnValue([{ timeZone: "Asia/Yekaterinburg" }]);

    expect(deviceTimeZone()).toBe("Asia/Yekaterinburg");
  });

  it("использует запасную зону, если определить не удалось", () => {
    (getCalendars as jest.Mock).mockReturnValue([{ timeZone: null }]);

    expect(deviceTimeZone()).toBe("Europe/Moscow");
  });
});
