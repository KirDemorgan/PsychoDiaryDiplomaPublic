import { renderHook, waitFor } from "@testing-library/react-native";

import { calendarApi } from "@/api/calendar-api";
import { queryWrapper } from "@/test/query-wrapper";

import { useCalendar, useDay } from "./use-calendar";

jest.mock("@/api/calendar-api", () => ({ calendarApi: { calendar: jest.fn(), day: jest.fn() } }));

const api = calendarApi as jest.Mocked<typeof calendarApi>;

describe("calendar hooks", () => {
  it("загружают календарь и день", async () => {
    api.calendar.mockResolvedValue({ days: [] } as never);
    api.day.mockResolvedValue({ day: {} } as never);
    const wrapper = queryWrapper();

    const calendar = await renderHook(() => useCalendar("2026-10-01", "2026-10-31", "UTC"), { wrapper });
    const day = await renderHook(() => useDay("2026-10-18", "UTC"), { wrapper });

    await waitFor(() => expect(calendar.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(day.result.current.isSuccess).toBe(true));
    expect(api.calendar).toHaveBeenCalledWith("2026-10-01", "2026-10-31", "UTC");
    expect(api.day).toHaveBeenCalledWith("2026-10-18", "UTC");
  });
});
