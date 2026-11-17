import { normalizeSelection, quoteOf } from "./selection";

describe("normalizeSelection", () => {
  const text = "Почти не спал. Тревожился.";

  it("возвращает выбранный фрагмент", () => {
    expect(normalizeSelection(text, { start: 0, end: 13 })).toEqual({ start: 0, end: 13 });
  });

  it("меняет местами перевёрнутые границы и ограничивает длиной текста", () => {
    expect(normalizeSelection(text, { start: 13, end: 0 })).toEqual({ start: 0, end: 13 });
    expect(normalizeSelection(text, { start: 15, end: 999 })).toEqual({ start: 15, end: text.length });
    expect(normalizeSelection(text, { start: -5, end: 5 })).toEqual({ start: 0, end: 5 });
  });

  it("обрезает пробелы по краям", () => {
    expect(normalizeSelection(text, { start: 14, end: 25 })).toEqual({ start: 15, end: 25 });
  });

  it("пустой или состоящий из пробелов выбор даёт null", () => {
    expect(normalizeSelection(text, { start: 4, end: 4 })).toBeNull();
    expect(normalizeSelection("а   б", { start: 1, end: 4 })).toBeNull();
  });

  it("не разрезает эмодзи (суррогатную пару)", () => {
    const withEmoji = "😀 грусть";
    expect(normalizeSelection(withEmoji, { start: 1, end: 9 })).toEqual({ start: 0, end: 9 });
    expect(normalizeSelection(withEmoji, { start: 0, end: 1 })).toEqual({ start: 0, end: 2 });
  });

  it("вырезает цитату по границам", () => {
    expect(quoteOf(text, { start: 0, end: 13 })).toBe("Почти не спал");
  });
});
