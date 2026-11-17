import { useQuery } from "@tanstack/react-query";

import { calendarApi } from "@/api/calendar-api";

export function useCalendar(from: string, to: string, timeZone: string) {
  return useQuery({
    queryKey: ["calendar", from, to, timeZone],
    queryFn: () => calendarApi.calendar(from, to, timeZone),
  });
}

export function useDay(date: string, timeZone: string) {
  return useQuery({ queryKey: ["day", date, timeZone], queryFn: () => calendarApi.day(date, timeZone) });
}
