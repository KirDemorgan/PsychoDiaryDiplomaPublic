const pad = (value: number, length = 2) => String(value).padStart(length, "0");

export function offsetDateTimeIn(timeZone: string, instant: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(instant);
  const read = (type: string) => Number.parseInt(parts.find((part) => part.type === type)?.value ?? "0", 10);

  const [year, month, day, hour, minute, second] = ["year", "month", "day", "hour", "minute", "second"].map(read);
  const wholeSeconds = Math.floor(instant.getTime() / 1000) * 1000;
  const offsetMinutes = Math.round((Date.UTC(year, month - 1, day, hour, minute, second) - wholeSeconds) / 60_000);

  const sign = offsetMinutes < 0 ? "-" : "+";
  const absolute = Math.abs(offsetMinutes);
  return (
    `${pad(year, 4)}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:${pad(second)}` +
    `${sign}${pad(Math.floor(absolute / 60))}:${pad(absolute % 60)}`
  );
}
