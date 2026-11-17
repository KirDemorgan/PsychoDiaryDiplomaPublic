export interface TextSelection {
  start: number;
  end: number;
}

const isHighSurrogate = (code: number) => code >= 0xd800 && code <= 0xdbff;
const isLowSurrogate = (code: number) => code >= 0xdc00 && code <= 0xdfff;

export function normalizeSelection(text: string, selection: TextSelection): TextSelection | null {
  let start = Math.max(0, Math.min(selection.start, selection.end));
  let end = Math.min(text.length, Math.max(selection.start, selection.end));

  if (start > 0 && isLowSurrogate(text.charCodeAt(start)) && isHighSurrogate(text.charCodeAt(start - 1))) {
    start -= 1;
  }
  if (end > 0 && end < text.length && isLowSurrogate(text.charCodeAt(end)) && isHighSurrogate(text.charCodeAt(end - 1))) {
    end += 1;
  }

  while (start < end && /\s/.test(text[start])) {
    start += 1;
  }
  while (end > start && /\s/.test(text[end - 1])) {
    end -= 1;
  }

  return end > start ? { start, end } : null;
}

export function quoteOf(text: string, selection: TextSelection): string {
  return text.slice(selection.start, selection.end);
}
