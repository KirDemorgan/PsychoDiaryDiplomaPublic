import type { Occurrence } from "@/api/medications-types";

import { MAX_PENDING_REMINDERS, planReminders } from "./plan";

const occurrence = (id: string, plannedAt: string, overrides: Partial<Occurrence> = {}): Occurrence => ({
  occurrenceId: id,
  medicationId: "m1",
  medicationName: "Препарат А",
  scheduleVersionId: "s1",
  scheduleSlotId: "sl1",
  plannedAt,
  plannedDose: "1",
  unit: "TABLET",
  timeZone: "UTC",
  status: "NO_DATA",
  intake: null,
  ...overrides,
});

const now = new Date("2026-10-18T10:00:00Z");

describe("planReminders", () => {
  it("планирует только будущие приёмы без отметки, по возрастанию времени", () => {
    const plan = planReminders(
      [
        occurrence("late", "2026-10-18T20:00:00Z"),
        occurrence("past", "2026-10-18T08:00:00Z"),
        occurrence("taken", "2026-10-18T12:00:00Z", { status: "TAKEN" }),
        occurrence("skipped", "2026-10-18T13:00:00Z", { status: "SKIPPED" }),
        occurrence("soon", "2026-10-18T11:00:00Z"),
      ],
      { now, neutralText: true },
    );

    expect(plan.map((reminder) => reminder.id)).toEqual(["soon", "late"]);
    expect(plan[0].at).toEqual(new Date("2026-10-18T11:00:00Z"));
  });

  it("с нейтральным текстом не раскрывает препарат и дозу", () => {
    const [reminder] = planReminders([occurrence("a", "2026-10-18T11:00:00Z")], { now, neutralText: true });

    expect(`${reminder.title} ${reminder.body}`).not.toMatch(/Препарат|таб/);
  });

  it("без нейтрального текста называет препарат и дозу", () => {
    const [reminder] = planReminders([occurrence("a", "2026-10-18T11:00:00Z")], { now, neutralText: false });

    expect(reminder.title).toBe("Препарат А");
    expect(reminder.body).toBe("Время приёма: 1 таб.");
  });

  it("не превышает лимит уведомлений iOS", () => {
    const many = Array.from({ length: MAX_PENDING_REMINDERS + 20 }, (_, index) =>
      occurrence(`o${index}`, new Date(now.getTime() + (index + 1) * 60_000).toISOString()),
    );

    expect(planReminders(many, { now, neutralText: true })).toHaveLength(MAX_PENDING_REMINDERS);
    expect(planReminders(many, { now, neutralText: true, limit: 5 })).toHaveLength(5);
  });

  it("возвращает пустой план без приёмов", () => {
    expect(planReminders([], { now, neutralText: true })).toEqual([]);
  });
});
