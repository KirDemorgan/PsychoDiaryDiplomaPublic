import { offsetDateTimeIn } from "./offset-date-time";

describe("offsetDateTimeIn", () => {
  const instant = new Date("2026-10-18T09:30:15.789Z");

  it.each([
    ["UTC", "2026-10-18T09:30:15+00:00"],
    ["Europe/Moscow", "2026-10-18T12:30:15+03:00"],
    ["Asia/Yekaterinburg", "2026-10-18T14:30:15+05:00"],
    ["America/New_York", "2026-10-18T05:30:15-04:00"],
    ["Asia/Kolkata", "2026-10-18T15:00:15+05:30"],
  ])("форматирует момент в зоне %s с верным смещением", (zone, expected) => {
    expect(offsetDateTimeIn(zone, instant)).toBe(expected);
  });

  it("учитывает переход на зимнее время", () => {
    expect(offsetDateTimeIn("America/New_York", new Date("2026-12-01T12:00:00Z"))).toBe("2026-12-01T07:00:00-05:00");
  });

  it("не даёт часу 24 в полночь", () => {
    expect(offsetDateTimeIn("UTC", new Date("2026-10-18T00:00:00Z"))).toBe("2026-10-18T00:00:00+00:00");
  });

  it("использует текущее время по умолчанию", () => {
    expect(offsetDateTimeIn("UTC")).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+00:00$/);
  });
});
