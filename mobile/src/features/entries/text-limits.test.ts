import { ENTRY_MAX_LENGTH, codePointLength, isEntryTextValid } from "./text-limits";

describe("text limits", () => {
  it("считает символы Unicode, а не кодовые единицы UTF-16", () => {
    expect(codePointLength("abc")).toBe(3);
    expect(codePointLength("😀😀")).toBe(2);
    expect(codePointLength("")).toBe(0);
  });

  it.each([
    ["пустой", "", false],
    ["только пробелы", "   \n", false],
    ["обычный", "Сегодня было тяжело", true],
    ["на границе", "я".repeat(ENTRY_MAX_LENGTH), true],
    ["за границей", "я".repeat(ENTRY_MAX_LENGTH + 1), false],
    ["эмодзи на границе", "😀".repeat(ENTRY_MAX_LENGTH), true],
  ])("текст %s", (_name, text, expected) => {
    expect(isEntryTextValid(text)).toBe(expected);
  });
});
