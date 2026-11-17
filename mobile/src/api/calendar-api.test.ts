import { createCalendarApi } from "./calendar-api";
import type { HttpClient } from "./http-client";

describe("calendarApi", () => {
  const request = jest.fn().mockResolvedValue({});
  const api = createCalendarApi({ request } as unknown as HttpClient);

  it("запрашивает календарь месяца и день с часовым поясом", async () => {
    await api.calendar("2026-10-01", "2026-10-31", "Asia/Yekaterinburg");
    await api.day("2026-10-18", "UTC");

    expect(request).toHaveBeenNthCalledWith(1, "/calendar?from=2026-10-01&to=2026-10-31&timeZone=Asia%2FYekaterinburg");
    expect(request).toHaveBeenNthCalledWith(2, "/days/2026-10-18?timeZone=UTC");
  });
});
