import { buildChart } from "./chart-geometry";

const options = { width: 112, height: 112, padding: { top: 6, right: 6, bottom: 6, left: 6 } };

describe("buildChart", () => {
  it("раскладывает точки равномерно и переворачивает ось Y", () => {
    const chart = buildChart([0, 10, 5], options);

    expect(chart.plot).toEqual({ left: 6, right: 106, top: 6, bottom: 106 });
    expect(chart.segments).toHaveLength(1);
    expect(chart.segments[0].points.map((point) => [point.x, point.y])).toEqual([
      [6, 106],
      [56, 6],
      [106, 56],
    ]);
  });

  it("строит линию и область под ней до нижней границы", () => {
    const [segment] = buildChart([0, 10], options).segments;

    expect(segment.line).toBe("M6 106 L106 6");
    expect(segment.area).toBe("M6 106 L106 6 L106 106 L6 106 Z");
  });

  it("пропуски разрывают линию на отдельные сегменты", () => {
    const chart = buildChart([2, 4, null, null, 6, 8], options);

    expect(chart.segments).toHaveLength(2);
    expect(chart.segments[0].points.map((point) => point.index)).toEqual([0, 1]);
    expect(chart.segments[1].points.map((point) => point.index)).toEqual([4, 5]);
  });

  it("одиночная точка рисуется без линии и области", () => {
    const [segment] = buildChart([null, 7, null], options).segments;

    expect(segment.points).toHaveLength(1);
    expect(segment.line).toBe("");
    expect(segment.area).toBe("");
  });

  it("не рисует сегменты, если данных нет", () => {
    expect(buildChart([null, null], options).segments).toEqual([]);
    expect(buildChart([], options).segments).toEqual([]);
  });

  it("центрирует единственную дату", () => {
    const chart = buildChart([5], options);

    expect(chart.segments[0].points[0].x).toBe(56);
  });

  it("ограничивает значения диапазоном шкалы", () => {
    const chart = buildChart([-5, 99], options);

    expect(chart.yFor(-5)).toBe(chart.plot.bottom);
    expect(chart.yFor(99)).toBe(chart.plot.top);
  });

  it("поддерживает свой диапазон шкалы", () => {
    const chart = buildChart([1], { ...options, min: 1, max: 1 });

    expect(chart.yFor(1)).toBe(chart.plot.bottom);
  });

  it("не даёт отрицательной области при слишком малой ширине", () => {
    const chart = buildChart([1, 2], { width: 4, height: 4, padding: { top: 6, right: 6, bottom: 6, left: 6 } });

    expect(chart.plot.right).toBeGreaterThanOrEqual(chart.plot.left);
    expect(chart.plot.bottom).toBeGreaterThanOrEqual(chart.plot.top);
  });
});
