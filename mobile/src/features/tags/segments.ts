export interface HighlightSpan {
  id: string;
  start: number;
  end: number;
}

export interface TextSegment {
  text: string;
  spanId: string | null;
}

export function buildSegments(text: string, spans: readonly HighlightSpan[]): TextSegment[] {
  const sorted = spans
    .filter((span) => span.start >= 0 && span.end > span.start && span.end <= text.length)
    .sort((left, right) => left.start - right.start || right.end - left.end);

  const segments: TextSegment[] = [];
  let cursor = 0;
  for (const span of sorted) {
    if (span.start < cursor) {
      continue;
    }
    if (span.start > cursor) {
      segments.push({ text: text.slice(cursor, span.start), spanId: null });
    }
    segments.push({ text: text.slice(span.start, span.end), spanId: span.id });
    cursor = span.end;
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), spanId: null });
  }
  return segments;
}
