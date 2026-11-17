import { areaPath, chartPoints, linePath } from "./chart-path";

describe("chart path", () => {
  it("раскладывает значения по ширине и инвертирует ось Y", () => {
    const points = chartPoints([0, 5, 10], 100, 60, 10);

    expect(points).toEqual([
      { x: 10, y: 50 },
      { x: 50, y: 30 },
      { x: 90, y: 10 },
    ]);
  });

  it("ограничивает значения шкалой", () => {
    const [low, high] = chartPoints([-3, 99], 100, 60, 10);

    expect(low.y).toBe(50);
    expect(high.y).toBe(10);
  });

  it("одна точка не даёт деления на ноль", () => {
    expect(chartPoints([5], 100, 60, 10)).toEqual([{ x: 10, y: 30 }]);
  });

  it("собирает линию и область под ней", () => {
    const points = chartPoints([0, 10], 100, 60, 10);

    expect(linePath(points)).toBe("M10.0 50.0 L90.0 10.0");
    expect(areaPath(points, 60)).toBe("M10.0 50.0 L90.0 10.0 L90.0 60 L10.0 60 Z");
    expect(areaPath([], 60)).toBe("");
  });
});
