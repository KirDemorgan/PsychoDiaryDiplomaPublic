import { buildSegments } from "./segments";

describe("buildSegments", () => {
  const text = "Почти не спал. Перед встречей тревожился.";

  it("делит текст на выделенные и обычные фрагменты", () => {
    expect(buildSegments(text, [{ id: "a", start: 0, end: 13 }])).toEqual([
      { text: "Почти не спал", spanId: "a" },
      { text: ". Перед встречей тревожился.", spanId: null },
    ]);
  });

  it("сортирует фрагменты и сохраняет обычный текст между ними", () => {
    const segments = buildSegments(text, [
      { id: "b", start: 15, end: 40 },
      { id: "a", start: 0, end: 13 },
    ]);

    expect(segments.map((segment) => segment.spanId)).toEqual(["a", null, "b", null]);
    expect(segments.map((segment) => segment.text).join("")).toBe(text);
  });

  it("при пересечении оставляет первый по позиции, а у равных начал — более длинный", () => {
    const segments = buildSegments("abcdef", [
      { id: "short", start: 0, end: 2 },
      { id: "long", start: 0, end: 4 },
      { id: "overlap", start: 3, end: 6 },
    ]);

    expect(segments).toEqual([
      { text: "abcd", spanId: "long" },
      { text: "ef", spanId: null },
    ]);
  });

  it.each([
    ["отрицательное начало", { id: "x", start: -1, end: 2 }],
    ["пустой диапазон", { id: "x", start: 2, end: 2 }],
    ["выход за пределы текста", { id: "x", start: 0, end: 99 }],
  ])("игнорирует некорректный диапазон: %s", (_name, span) => {
    expect(buildSegments("abc", [span])).toEqual([{ text: "abc", spanId: null }]);
  });

  it("возвращает пустой список для пустого текста", () => {
    expect(buildSegments("", [])).toEqual([]);
  });

  it("не режет эмодзи, если позиции заданы в UTF-16", () => {
    const withEmoji = "😀 грусть";
    const segments = buildSegments(withEmoji, [{ id: "a", start: 3, end: 9 }]);

    expect(segments).toEqual([
      { text: "😀 ", spanId: null },
      { text: "грусть", spanId: "a" },
    ]);
  });
});
