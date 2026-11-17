import { fireEvent, render, screen } from "@testing-library/react-native";

import { ApiError } from "@/api/errors";
import { ratingsApi } from "@/api/ratings-api";
import type { RatingResponse } from "@/api/ratings-types";
import { queryWrapper } from "@/test/query-wrapper";

import { RatingsCard } from "./ratings-card";

jest.mock("@/api/ratings-api", () => ({ ratingsApi: { listForRange: jest.fn(), save: jest.fn(), remove: jest.fn() } }));
jest.mock("@react-native-community/slider", () => jest.requireActual("@/test/mocks").sliderMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));

const api = ratingsApi as jest.Mocked<typeof ratingsApi>;

const eveningRating: RatingResponse = {
  id: "r1",
  localDate: "2026-10-18",
  slot: "EVENING",
  timeZone: "UTC",
  anxiety: 8,
  mood: null,
  energy: null,
  createdAt: "2026-10-18T20:00:00Z",
  updatedAt: "2026-10-18T20:00:00Z",
  version: 0,
};

async function renderCard() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <RatingsCard />
    </Wrapper>,
  );
}

describe("RatingsCard", () => {
  beforeEach(() => {
    jest.useFakeTimers({ now: new Date("2026-10-18T09:00:00Z"), doNotFake: ["nextTick", "setImmediate", "queueMicrotask"] });
    api.listForRange.mockReset();
  });

  afterEach(() => jest.useRealTimers());

  it("выбирает слот по текущему времени и запрашивает сегодняшнюю дату", async () => {
    api.listForRange.mockResolvedValue([]);
    await renderCard();

    expect(await screen.findByRole("button", { name: "Сохранить оценку" })).toBeTruthy();
    expect(screen.getByRole("radio", { name: "Утро" })).toBeSelected();
    expect(api.listForRange).toHaveBeenCalledWith("2026-10-18", "2026-10-18");
  });

  it("подставляет сохранённую оценку выбранного слота", async () => {
    api.listForRange.mockResolvedValue([eveningRating]);
    await renderCard();
    await screen.findByRole("button", { name: "Сохранить оценку" });

    await fireEvent.press(screen.getByRole("radio", { name: "Вечер" }));

    expect(await screen.findByRole("button", { name: "Обновить оценку" })).toBeTruthy();
    expect(screen.getByText("8/10")).toBeTruthy();
  });

  it("показывает ошибку загрузки и повторяет запрос", async () => {
    api.listForRange.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue([]);
    await renderCard();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByRole("button", { name: "Сохранить оценку" })).toBeTruthy();
  });
});
