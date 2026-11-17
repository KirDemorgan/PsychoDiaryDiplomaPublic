const LOCALE = "ru-RU";

export function formatLongDate(date: Date): string {
  const text = new Intl.DateTimeFormat(LOCALE, { weekday: "long", day: "numeric", month: "long" }).format(date);
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function greeting(displayName: string | null | undefined): string {
  const name = displayName?.trim();
  return name ? `Здравствуйте, ${name}` : "Здравствуйте";
}

export function formatEntryMoment(isoInstant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(isoInstant));
}

export function formatDay(isoInstant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, { timeZone, day: "numeric", month: "long", year: "numeric" }).format(
    new Date(isoInstant),
  );
}

export function formatDateOnly(localDate: string): string {
  const [year, month, day] = localDate.split("-").map(Number);
  return new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "long", year: "numeric" }).format(
    new Date(year, month - 1, day),
  );
}

export function joinSentences(sentences: readonly string[]): string {
  return sentences
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length > 0)
    .map((sentence) => (/[.!?…]$/.test(sentence) ? sentence : `${sentence}.`))
    .join(" ");
}
