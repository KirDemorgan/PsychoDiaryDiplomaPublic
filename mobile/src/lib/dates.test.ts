import { currentSlot, currentTime, localDateIn, localHourIn, msUntil, slotForHour } from "./dates";

describe("dates", () => {
  const instant = new Date("2026-10-17T21:30:00Z");

  it("берёт локальную дату в указанной зоне, а не в UTC", () => {
    expect(localDateIn("UTC", instant)).toBe("2026-10-17");
    expect(localDateIn("Europe/Moscow", instant)).toBe("2026-10-18");
    expect(localDateIn("Pacific/Auckland", instant)).toBe("2026-10-18");
  });

  it("берёт локальный час в указанной зоне", () => {
    expect(localHourIn("UTC", instant)).toBe(21);
    expect(localHourIn("Europe/Moscow", instant)).toBe(0);
  });

  it.each([
    [0, "MORNING"],
    [10, "MORNING"],
    [11, "DAY"],
    [16, "DAY"],
    [17, "EVENING"],
    [23, "EVENING"],
  ])("час %i относится к слоту %s", (hour, slot) => {
    expect(slotForHour(hour)).toBe(slot);
  });

  it("определяет слот по зоне", () => {
    expect(currentSlot("UTC", instant)).toBe("EVENING");
    expect(currentSlot("Europe/Moscow", instant)).toBe("MORNING");
  });
});

describe("msUntil", () => {
  const now = new Date("2026-10-18T10:00:00Z");

  it("считает миллисекунды до будущего момента и не уходит в минус для прошлого", () => {
    expect(msUntil("2026-10-18T10:00:30Z", now)).toBe(30_000);
    expect(msUntil("2026-10-18T09:00:00Z", now)).toBe(0);
  });

  it("берёт текущее время по умолчанию", () => {
    expect(msUntil(new Date(Date.now() + 5_000).toISOString())).toBeGreaterThan(0);
    expect(currentTime()).toBeInstanceOf(Date);
  });
});
