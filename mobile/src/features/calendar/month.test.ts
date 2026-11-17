import { monthBounds, monthGrid, monthOf, monthTitle, shiftMonth, WEEKDAY_LABELS } from "./month";

describe("calendar month helpers", () => {
  it("определяет месяц по дате", () => {
    expect(monthOf("2026-10-18")).toEqual({ year: 2026, month: 10 });
  });

  it.each([
    [{ year: 2026, month: 10 }, 1, { year: 2026, month: 11 }],
    [{ year: 2026, month: 12 }, 1, { year: 2027, month: 1 }],
    [{ year: 2026, month: 1 }, -1, { year: 2025, month: 12 }],
    [{ year: 2026, month: 3 }, -15, { year: 2024, month: 12 }],
    [{ year: 2026, month: 3 }, 0, { year: 2026, month: 3 }],
  ])("сдвигает %j на %i месяцев", (ref, delta, expected) => {
    expect(shiftMonth(ref, delta)).toEqual(expected);
  });

  it("находит первый и последний день месяца, включая февраль високосного года", () => {
    expect(monthBounds({ year: 2026, month: 10 })).toEqual({ from: "2026-10-01", to: "2026-10-31" });
    expect(monthBounds({ year: 2028, month: 2 })).toEqual({ from: "2028-02-01", to: "2028-02-29" });
    expect(monthBounds({ year: 2026, month: 2 })).toEqual({ from: "2026-02-01", to: "2026-02-28" });
  });

  it("строит сетку с понедельника и дополняет недели пустыми ячейками", () => {
    const grid = monthGrid({ year: 2026, month: 10 });

    expect(grid.every((week) => week.length === 7)).toBe(true);
    expect(grid[0]).toEqual([null, null, null, "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"]);
    expect(grid.at(-1)).toEqual(["2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", null]);
    expect(grid.flat().filter(Boolean)).toHaveLength(31);
  });

  it("месяц, начинающийся с понедельника, не имеет пустых ячеек в начале", () => {
    const grid = monthGrid({ year: 2026, month: 6 });

    expect(grid[0][0]).toBe("2026-06-01");
  });

  it("месяц, начинающийся с воскресенья, сдвигается на шесть ячеек", () => {
    const grid = monthGrid({ year: 2026, month: 2 });

    expect(grid[0].slice(0, 6)).toEqual([null, null, null, null, null, null]);
    expect(grid[0][6]).toBe("2026-02-01");
  });

  it("называет месяц по-русски с заглавной буквы", () => {
    expect(monthTitle({ year: 2026, month: 10 })).toBe("Октябрь 2026");
    expect(WEEKDAY_LABELS).toHaveLength(7);
  });
});
