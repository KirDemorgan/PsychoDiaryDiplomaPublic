import type { RatingResponse, RatingValues } from "@/api/ratings-types";

export const EMPTY_RATING: RatingValues = { anxiety: null, mood: null, energy: null };

export const RATING_MIN = 0;
export const RATING_MAX = 10;

export function valuesOf(rating: RatingResponse | undefined): RatingValues {
  return rating
    ? { anxiety: rating.anxiety, mood: rating.mood, energy: rating.energy }
    : EMPTY_RATING;
}

export function hasAnyValue(values: RatingValues): boolean {
  return values.anxiety !== null || values.mood !== null || values.energy !== null;
}

export function sameValues(left: RatingValues, right: RatingValues): boolean {
  return left.anxiety === right.anxiety && left.mood === right.mood && left.energy === right.energy;
}
