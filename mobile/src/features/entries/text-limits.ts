export const ENTRY_MAX_LENGTH = 5000;

export function codePointLength(text: string): number {
  return Array.from(text).length;
}

export function isEntryTextValid(text: string): boolean {
  const length = codePointLength(text.trim());
  return length >= 1 && codePointLength(text) <= ENTRY_MAX_LENGTH;
}
