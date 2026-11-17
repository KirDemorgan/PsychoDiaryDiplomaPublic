export type RatingSlot = "MORNING" | "DAY" | "EVENING";

export const RATING_SLOTS: readonly RatingSlot[] = ["MORNING", "DAY", "EVENING"];

export type RatingValue = number | null;

export interface RatingValues {
  anxiety: RatingValue;
  mood: RatingValue;
  energy: RatingValue;
}

export interface RatingResponse extends RatingValues {
  id: string;
  localDate: string;
  slot: RatingSlot;
  timeZone: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
