import { describeSlot, formatClock, formatDays, formatDose, formatPlannedTime } from "./format";

describe("medication formatting", () => {
  it.each([
    ["10", "MG", "10 мг"],
    ["12.5", "ML", "12,5 мл"],
    [0.5, "TABLET", "0,5 таб."],
    ["2.000", "DROP", "2 капли"],
    ["abc", "UNIT", "abc ед."],
  ] as const)("доза %s %s → %s", (dose, unit, expected) => {
    expect(formatDose(dose, unit)).toBe(expected);
  });

  it("называет дни недели по порядку и сворачивает «каждый день»", () => {
    expect(formatDays(["SUNDAY", "MONDAY", "WEDNESDAY"])).toBe("Пн, Ср, Вс");
    expect(formatDays(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"])).toBe("Каждый день");
  });

  it("обрезает секунды во времени приёма", () => {
    expect(formatClock("08:30:00")).toBe("08:30");
    expect(formatClock("21:05")).toBe("21:05");
  });

  it("описывает слот одной строкой", () => {
    expect(describeSlot({ localTime: "08:00:00", daysOfWeek: ["MONDAY", "FRIDAY"], dose: "1", unit: "TABLET" })).toBe(
      "08:00 · 1 таб. · Пн, Пт",
    );
  });

  it("показывает плановое время в часовом поясе схемы", () => {
    expect(formatPlannedTime("2026-10-18T03:30:00Z", "Asia/Yekaterinburg")).toBe("08:30");
    expect(formatPlannedTime("2026-10-18T03:30:00Z", "UTC")).toBe("03:30");
  });
});
