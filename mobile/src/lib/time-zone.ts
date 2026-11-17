import { getCalendars } from "expo-localization";

const FALLBACK_TIME_ZONE = "Europe/Moscow";

export function deviceTimeZone(): string {
  return getCalendars()[0]?.timeZone ?? FALLBACK_TIME_ZONE;
}
