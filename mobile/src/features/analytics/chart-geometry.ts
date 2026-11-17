export interface ChartPoint {
  x: number;
  y: number;
  value: number;
  index: number;
}

export interface ChartSegment {
  points: ChartPoint[];
  line: string;
  area: string;
}

export interface ChartPadding {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface ChartOptions {
  width: number;
  height: number;
  padding: ChartPadding;
  min?: number;
  max?: number;
}

export interface Chart {
  segments: ChartSegment[];
  plot: { left: number; right: number; top: number; bottom: number };
  xFor: (index: number) => number;
  yFor: (value: number) => number;
}

const round = (value: number) => Math.round(value * 100) / 100;

export function buildChart(values: readonly (number | null)[], options: ChartOptions): Chart {
  const { width, height, padding, min = 0, max = 10 } = options;
  const plot = {
    left: padding.left,
    right: Math.max(padding.left, width - padding.right),
    top: padding.top,
    bottom: Math.max(padding.top, height - padding.bottom),
  };
  const span = max - min || 1;
  const steps = Math.max(values.length - 1, 1);

  const xFor = (index: number) =>
    values.length <= 1 ? (plot.left + plot.right) / 2 : plot.left + ((plot.right - plot.left) * index) / steps;
  const yFor = (value: number) => {
    const clamped = Math.min(Math.max(value, min), max);
    return plot.bottom - ((plot.bottom - plot.top) * (clamped - min)) / span;
  };

  const runs: ChartPoint[][] = [];
  let current: ChartPoint[] = [];
  values.forEach((value, index) => {
    if (value === null) {
      if (current.length > 0) {
        runs.push(current);
        current = [];
      }
      return;
    }
    current.push({ x: round(xFor(index)), y: round(yFor(value)), value, index });
  });
  if (current.length > 0) {
    runs.push(current);
  }

  const segments = runs.map((points) => {
    const line = points.length > 1 ? points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x} ${p.y}`).join(" ") : "";
    const first = points[0];
    const last = points[points.length - 1];
    const area = points.length > 1 ? `${line} L${last.x} ${plot.bottom} L${first.x} ${plot.bottom} Z` : "";
    return { points, line, area };
  });

  return { segments, plot, xFor, yFor };
}
