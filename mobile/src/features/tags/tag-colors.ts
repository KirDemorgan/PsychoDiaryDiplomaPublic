import type { TagCode } from "@/api/tags-types";
import type { Palette } from "@/theme/colors";

export const TAG_LABELS: Record<TagCode, string> = {
  SLEEP_DEFICIT: "Недостаток сна",
  WORK_STUDY: "Работа и учёба",
  SOCIAL_EVENT: "Социальное событие",
  CONFLICT: "Конфликт",
  PHYSICAL_ACTIVITY: "Физическая активность",
  CAFFEINE: "Кофеин",
  ANXIETY: "Тревога",
  CALM: "Спокойствие",
  FATIGUE: "Усталость",
  NAUSEA: "Тошнота",
  HEADACHE: "Головная боль",
  TREMOR: "Дрожь",
};

export const TAG_CODES = Object.keys(TAG_LABELS) as TagCode[];

export function tagLabel(code: string): string {
  return TAG_LABELS[code as TagCode] ?? code;
}

export function tagColor(code: TagCode, palette: Palette): string {
  switch (code) {
    case "SLEEP_DEFICIT":
      return palette.tagSleep;
    case "ANXIETY":
      return palette.tagAnxiety;
    case "CALM":
      return palette.tagCalm;
    case "NAUSEA":
    case "HEADACHE":
    case "TREMOR":
      return palette.tagSymptom;
    case "FATIGUE":
      return palette.tagState;
    default:
      return palette.tagContext;
  }
}
