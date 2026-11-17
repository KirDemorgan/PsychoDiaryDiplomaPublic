import type { TagPresence, TagTemporal } from "@/api/tags-types";

export const PRESENCE_LABELS: Record<TagPresence, string> = {
  PRESENT: "Есть",
  NEGATED: "Нет",
  UNCERTAIN: "Неясно",
};

export const TEMPORAL_LABELS: Record<TagTemporal, string> = {
  CURRENT: "Сейчас",
  PAST: "Раньше",
  FUTURE: "Позже",
  UNSPECIFIED: "Не указано",
};

export const PRESENCE_OPTIONS = (Object.keys(PRESENCE_LABELS) as TagPresence[]).map((value) => ({
  value,
  label: PRESENCE_LABELS[value],
}));

export const TEMPORAL_OPTIONS = (Object.keys(TEMPORAL_LABELS) as TagTemporal[]).map((value) => ({
  value,
  label: TEMPORAL_LABELS[value],
}));
