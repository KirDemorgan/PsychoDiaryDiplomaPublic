import {
  dateToTime,
  isDoseValid,
  newSlotDraft,
  normalizeDose,
  parseDose,
  timeToDate,
  toScheduleInput,
  validateSlots,
  type SlotDraft,
} from "./schedule-form";

const slot = (overrides: Partial<SlotDraft> = {}): SlotDraft => ({ ...newSlotDraft("k"), dose: "1", ...overrides });

describe("schedule form", () => {
  it("новый слот по умолчанию на каждый день в 09:00", () => {
    const draft = newSlotDraft("a");

    expect(draft).toMatchObject({ key: "a", time: "09:00", dose: "" });
    expect(draft.days).toHaveLength(7);
  });

  it.each([
    ["1", true],
    ["0,5", true],
    ["12.25", true],
    [" 3 ", true],
    ["", false],
    ["0", false],
    ["-1", false],
    ["abc", false],
    ["1.2345", false],
    ["1000000", false],
  ])("доза %p допустима: %s", (text, expected) => {
    expect(isDoseValid(text)).toBe(expected);
  });

  it("приводит запятую к точке", () => {
    expect(normalizeDose(" 0,5 ")).toBe("0.5");
  });

  it("проверяет набор слотов", () => {
    expect(validateSlots([])).toBe("Добавьте хотя бы один приём");
    expect(validateSlots([slot({ days: [] })])).toBe("Выберите дни приёма");
    expect(validateSlots([slot({ dose: "" })])).toBe("Укажите дозу числом больше нуля");
    expect(validateSlots([slot()])).toBeNull();
  });

  it("собирает запрос схемы с днями в порядке недели и точкой в дозе", () => {
    const input = toScheduleInput(
      [slot({ days: ["SUNDAY", "MONDAY"], dose: "0,5", unit: "MG", time: "21:30" })],
      "2026-10-18T10:00:00+05:00",
      "Asia/Yekaterinburg",
      "2026-11-01",
    );

    expect(input).toEqual({
      effectiveFrom: "2026-10-18T10:00:00+05:00",
      treatmentEndsOn: "2026-11-01",
      timeZone: "Asia/Yekaterinburg",
      slots: [{ localTime: "21:30", daysOfWeek: ["MONDAY", "SUNDAY"], dose: 0.5, unit: "MG" }],
    });
  });

  it("преобразует время в дату и обратно", () => {
    const base = new Date(2026, 9, 18, 3, 3, 3);

    const date = timeToDate("07:05", base);

    expect(date.getHours()).toBe(7);
    expect(date.getMinutes()).toBe(5);
    expect(date.getDate()).toBe(18);
    expect(dateToTime(date)).toBe("07:05");
    expect(timeToDate("07:05").getHours()).toBe(7);
  });
});

describe("parseDose", () => {
  it("превращает ввод с запятой в число для JSON, как требует бэкенд без приведения типов", () => {
    expect(parseDose("0,5")).toBe(0.5);
    expect(parseDose(" 12.25 ")).toBe(12.25);
    expect(typeof parseDose("1")).toBe("number");
  });
});
