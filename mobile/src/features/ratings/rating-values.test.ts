import type { RatingResponse } from "@/api/ratings-types";

import { EMPTY_RATING, hasAnyValue, sameValues, valuesOf } from "./rating-values";

const rating: RatingResponse = {
  id: "r1",
  localDate: "2026-10-18",
  slot: "DAY",
  timeZone: "Europe/Moscow",
  anxiety: 4,
  mood: null,
  energy: 0,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-18T10:00:00Z",
  version: 1,
};

describe("rating values", () => {
  it("извлекает значения и сохраняет ноль отдельно от пустого", () => {
    expect(valuesOf(rating)).toEqual({ anxiety: 4, mood: null, energy: 0 });
    expect(valuesOf(undefined)).toBe(EMPTY_RATING);
  });

  it("считает ноль заполненным значением", () => {
    expect(hasAnyValue({ anxiety: null, mood: null, energy: 0 })).toBe(true);
    expect(hasAnyValue(EMPTY_RATING)).toBe(false);
  });

  it("сравнивает наборы значений", () => {
    expect(sameValues(valuesOf(rating), { anxiety: 4, mood: null, energy: 0 })).toBe(true);
    expect(sameValues(valuesOf(rating), { anxiety: 4, mood: 5, energy: 0 })).toBe(false);
    expect(sameValues(EMPTY_RATING, { anxiety: null, mood: null, energy: 0 })).toBe(false);
  });
});
