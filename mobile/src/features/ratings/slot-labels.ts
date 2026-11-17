import { RATING_SLOTS, type RatingSlot } from "@/api/ratings-types";

export const SLOT_LABELS: Record<RatingSlot, string> = {
  MORNING: "Утро",
  DAY: "День",
  EVENING: "Вечер",
};

export const SLOT_OPTIONS = RATING_SLOTS.map((slot) => ({ value: slot, label: SLOT_LABELS[slot] }));
