import { darkPalette, lightPalette } from "@/theme/colors";

import { TAG_CODES, TAG_LABELS, tagColor } from "./tag-colors";
import { PRESENCE_OPTIONS, TEMPORAL_OPTIONS } from "./labels";

describe("tag colors and labels", () => {
  it("содержит все 12 кодов словаря с русскими названиями", () => {
    expect(TAG_CODES).toHaveLength(12);
    expect(TAG_LABELS.SLEEP_DEFICIT).toBe("Недостаток сна");
    expect(Object.values(TAG_LABELS).every((label) => label.length > 0)).toBe(true);
  });

  it("совпадает с цветами концепта для сна, тревоги и спокойствия", () => {
    expect(tagColor("SLEEP_DEFICIT", lightPalette)).toBe(lightPalette.tagSleep);
    expect(tagColor("ANXIETY", lightPalette)).toBe(lightPalette.tagAnxiety);
    expect(tagColor("CALM", darkPalette)).toBe(darkPalette.tagCalm);
  });

  it("красит симптомы, состояние и контекст по группам", () => {
    for (const code of ["NAUSEA", "HEADACHE", "TREMOR"] as const) {
      expect(tagColor(code, lightPalette)).toBe(lightPalette.tagSymptom);
    }
    expect(tagColor("FATIGUE", lightPalette)).toBe(lightPalette.tagState);
    for (const code of ["WORK_STUDY", "SOCIAL_EVENT", "CONFLICT", "PHYSICAL_ACTIVITY", "CAFFEINE"] as const) {
      expect(tagColor(code, lightPalette)).toBe(lightPalette.tagContext);
    }
  });

  it("описывает варианты наличия и времени", () => {
    expect(PRESENCE_OPTIONS.map((option) => option.label)).toEqual(["Есть", "Нет", "Неясно"]);
    expect(TEMPORAL_OPTIONS).toHaveLength(4);
  });
});
