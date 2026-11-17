import type { DailyValue } from "@/api/analytics-types";
import { darkPalette, lightPalette } from "@/theme/colors";

import { eachDate, periodRange, shortDate } from "./period";
import {
  dailyMean,
  dailySeries,
  formatDecimal,
  formatPercent,
  isFilledDay,
  METRIC_OPTIONS,
  metricColor,
  toNumber,
} from "./metrics";

const day = (overrides: Partial<DailyValue> = {}): DailyValue => ({
  date: "2026-10-16",
  anxietyMean: "6.5000",
  anxietySlotCount: 2,
  moodMean: null,
  moodSlotCount: 0,
  energyMean: 3,
  energySlotCount: 1,
  ...overrides,
});

describe("period helpers", () => {
  it("считает период включительно, заканчивающийся сегодня", () => {
    const now = new Date("2026-10-18T12:00:00Z");

    expect(periodRange(7, "UTC", now)).toEqual({ from: "2026-10-12", to: "2026-10-18" });
    expect(periodRange(1, "UTC", now)).toEqual({ from: "2026-10-18", to: "2026-10-18" });
  });

  it("учитывает часовой пояс при выборе сегодняшней даты", () => {
    const now = new Date("2026-10-17T21:30:00Z");

    expect(periodRange(3, "Europe/Moscow", now).to).toBe("2026-10-18");
  });

  it("перечисляет даты периода, в том числе через границу месяца и года", () => {
    expect(eachDate("2026-10-30", "2026-11-02")).toEqual(["2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02"]);
    expect(eachDate("2026-12-31", "2027-01-01")).toEqual(["2026-12-31", "2027-01-01"]);
    expect(eachDate("2026-10-18", "2026-10-18")).toEqual(["2026-10-18"]);
    expect(eachDate("2026-10-19", "2026-10-18")).toEqual([]);
  });

  it("сокращает дату до дня и месяца", () => {
    expect(shortDate("2026-10-05")).toBe("05.10");
  });
});

describe("metric helpers", () => {
  it.each([
    ["6.5", 6.5],
    [3, 3],
    [null, null],
    [undefined, null],
    ["abc", null],
  ])("приводит %p к числу %p", (value, expected) => {
    expect(toNumber(value as never)).toBe(expected);
  });

  it("берёт среднее дня по нужному показателю", () => {
    expect(dailyMean(day(), "ANXIETY")).toBe(6.5);
    expect(dailyMean(day(), "MOOD")).toBeNull();
    expect(dailyMean(day(), "ENERGY")).toBe(3);
  });

  it("строит ряд по всем датам периода, оставляя пропуски пустыми", () => {
    const series = dailySeries([day()], "ANXIETY", ["2026-10-15", "2026-10-16", "2026-10-17"]);

    expect(series).toEqual([
      { date: "2026-10-15", value: null },
      { date: "2026-10-16", value: 6.5 },
      { date: "2026-10-17", value: null },
    ]);
  });

  it("считает день заполненным, если есть хотя бы один слот", () => {
    expect(isFilledDay(day())).toBe(true);
    expect(isFilledDay(day({ anxietySlotCount: 0, energySlotCount: 0 }))).toBe(false);
  });

  it("форматирует десятичные значения и проценты по-русски", () => {
    expect(formatDecimal("6.25")).toBe("6,3");
    expect(formatDecimal(4, 0)).toBe("4");
    expect(formatDecimal(null)).toBe("—");
    expect(formatPercent("0.6667")).toBe("67%");
    expect(formatPercent(null)).toBe("—");
  });

  it("задаёт цвета и подписи показателей", () => {
    expect(metricColor("ANXIETY", lightPalette)).toBe(lightPalette.anxiety);
    expect(metricColor("MOOD", darkPalette)).toBe(darkPalette.mood);
    expect(metricColor("ENERGY", lightPalette)).toBe(lightPalette.energy);
    expect(METRIC_OPTIONS.map((option) => option.label)).toEqual(["Тревожность", "Настроение", "Энергия"]);
  });
});
