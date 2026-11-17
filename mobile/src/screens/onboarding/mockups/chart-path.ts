export interface ChartPoint {
  x: number;
  y: number;
}

export function chartPoints(values: readonly number[], width: number, height: number, padding: number, max = 10): ChartPoint[] {
  const innerWidth = width - padding * 2;
  const innerHeight = height - padding * 2;
  const step = values.length > 1 ? innerWidth / (values.length - 1) : 0;
  return values.map((value, index) => ({
    x: padding + step * index,
    y: padding + innerHeight - (Math.min(Math.max(value, 0), max) / max) * innerHeight,
  }));
}

export function linePath(points: readonly ChartPoint[]): string {
  return points.map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
}

export function areaPath(points: readonly ChartPoint[], baseline: number): string {
  if (points.length === 0) {
    return "";
  }
  const first = points[0];
  const last = points[points.length - 1];
  return `${linePath(points)} L${last.x.toFixed(1)} ${baseline} L${first.x.toFixed(1)} ${baseline} Z`;
}
