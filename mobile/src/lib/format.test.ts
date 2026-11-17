import { formatEntryMoment, formatLongDate, greeting, joinSentences } from "./format";

describe("greeting", () => {
  it("использует имя пользователя", () => {
    expect(greeting("Анна")).toBe("Здравствуйте, Анна");
  });

  it.each([null, undefined, "", "   "])("без имени (%p) обходится без него", (name) => {
    expect(greeting(name)).toBe("Здравствуйте");
  });
});

describe("formatLongDate", () => {
  it("возвращает русскую дату с заглавной буквы", () => {
    const text = formatLongDate(new Date(2026, 9, 2));

    expect(text).toMatch(/^П/);
    expect(text).toContain("октября");
  });
});

describe("formatEntryMoment", () => {
  it("показывает дату и время в часовом поясе записи", () => {
    const iso = "2026-10-18T21:30:00Z";

    expect(formatEntryMoment(iso, "UTC")).toContain("21:30");
    expect(formatEntryMoment(iso, "Europe/Moscow")).toContain("00:30");
    expect(formatEntryMoment(iso, "Europe/Moscow")).toContain("19");
  });
});

describe("joinSentences", () => {
  it("ставит точку там, где её нет, и не дублирует существующую", () => {
    expect(joinSentences(["Пропуски не интерполируются", "Порог — правило интерфейса.", "Вопрос?"])).toBe(
      "Пропуски не интерполируются. Порог — правило интерфейса. Вопрос?",
    );
  });

  it("пропускает пустые строки и пробелы по краям", () => {
    expect(joinSentences(["  Один  ", "", "   "])).toBe("Один.");
    expect(joinSentences([])).toBe("");
  });
});
