export interface MonthRef {
  year: number;
  month: number;
}

const pad = (value: number) => String(value).padStart(2, "0");

export function monthOf(localDate: string): MonthRef {
  const [year, month] = localDate.split("-").map(Number);
  return { year, month };
}

export function shiftMonth(ref: MonthRef, delta: number): MonthRef {
  const index = ref.year * 12 + (ref.month - 1) + delta;
  return { year: Math.floor(index / 12), month: (index % 12) + 1 };
}

export function monthBounds(ref: MonthRef): { from: string; to: string } {
  const last = new Date(Date.UTC(ref.year, ref.month, 0)).getUTCDate();
  return { from: `${ref.year}-${pad(ref.month)}-01`, to: `${ref.year}-${pad(ref.month)}-${pad(last)}` };
}

export function monthGrid(ref: MonthRef): (string | null)[][] {
  const { to } = monthBounds(ref);
  const lastDay = Number(to.slice(8));
  const firstWeekday = (new Date(Date.UTC(ref.year, ref.month - 1, 1)).getUTCDay() + 6) % 7;
  const cells: (string | null)[] = Array(firstWeekday).fill(null);
  for (let day = 1; day <= lastDay; day += 1) {
    cells.push(`${ref.year}-${pad(ref.month)}-${pad(day)}`);
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  const weeks: (string | null)[][] = [];
  for (let index = 0; index < cells.length; index += 7) {
    weeks.push(cells.slice(index, index + 7));
  }
  return weeks;
}

export function monthTitle(ref: MonthRef): string {
  const text = new Intl.DateTimeFormat("ru-RU", { month: "long", year: "numeric", timeZone: "UTC" }).format(
    new Date(Date.UTC(ref.year, ref.month - 1, 1)),
  );
  const title = text.replace(/\s*г\.$/, "");
  return title.charAt(0).toUpperCase() + title.slice(1);
}

export const WEEKDAY_LABELS = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"] as const;
