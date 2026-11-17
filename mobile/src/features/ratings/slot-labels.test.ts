import { SLOT_LABELS, SLOT_OPTIONS } from "./slot-labels";

describe("slot labels", () => {
  it("называет три слота по-русски в порядке суток", () => {
    expect(SLOT_OPTIONS.map((option) => option.label)).toEqual(["Утро", "День", "Вечер"]);
    expect(SLOT_LABELS.EVENING).toBe("Вечер");
  });
});
