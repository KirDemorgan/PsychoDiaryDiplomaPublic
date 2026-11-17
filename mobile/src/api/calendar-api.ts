import type { CalendarResponse, DayResponse } from "./calendar-types";
import { httpClient, type HttpClient } from "./http-client";

export function createCalendarApi(client: HttpClient) {
  return {
    calendar: (from: string, to: string, timeZone: string) =>
      client.request<CalendarResponse>(`/calendar?${new URLSearchParams({ from, to, timeZone })}`),

    day: (date: string, timeZone: string) =>
      client.request<DayResponse>(`/days/${date}?${new URLSearchParams({ timeZone })}`),
  };
}

export type CalendarApi = ReturnType<typeof createCalendarApi>;

export const calendarApi = createCalendarApi(httpClient);
