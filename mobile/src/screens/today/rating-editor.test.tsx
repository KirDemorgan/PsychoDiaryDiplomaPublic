import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError, NETWORK_ERROR } from "@/api/errors";
import { ratingsApi } from "@/api/ratings-api";
import type { RatingResponse } from "@/api/ratings-types";
import { queryWrapper } from "@/test/query-wrapper";

import { RatingEditor } from "./rating-editor";

jest.mock("@/api/ratings-api", () => ({ ratingsApi: { save: jest.fn(), remove: jest.fn(), listForRange: jest.fn() } }));
jest.mock("@react-native-community/slider", () => jest.requireActual("@/test/mocks").sliderMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "fixed-key" }));

const api = ratingsApi as jest.Mocked<typeof ratingsApi>;

const existing: RatingResponse = {
  id: "r1",
  localDate: "2026-10-18",
  slot: "DAY",
  timeZone: "Europe/Moscow",
  anxiety: 4,
  mood: 6,
  energy: null,
  createdAt: "2026-10-18T10:00:00Z",
  updatedAt: "2026-10-18T10:00:00Z",
  version: 2,
};

async function renderEditor(rating?: RatingResponse) {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <RatingEditor date="2026-10-18" slot="DAY" timeZone="Europe/Moscow" existing={rating} />
    </Wrapper>,
  );
}

describe("RatingEditor", () => {
  beforeEach(() => {
    api.save.mockReset();
    api.remove.mockReset();
    api.listForRange.mockResolvedValue([]);
  });

  it("не даёт сохранить пустую оценку", async () => {
    await renderEditor();

    expect(screen.getByRole("button", { name: "Сохранить оценку" })).toBeDisabled();
  });

  it("создаёт оценку с введёнными значениями и ключом идемпотентности", async () => {
    api.save.mockResolvedValue(existing);
    await renderEditor();

    await fireEvent(screen.getByLabelText("Тревожность"), "slidingComplete", 3);
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить оценку" }));

    await waitFor(() =>
      expect(api.save).toHaveBeenCalledWith({
        date: "2026-10-18",
        slot: "DAY",
        values: { anxiety: 3, mood: null, energy: null },
        timeZone: "Europe/Moscow",
        existing: undefined,
        idempotencyKey: "fixed-key",
      }),
    );
  });

  it("обновляет существующую оценку, передавая её версию", async () => {
    api.save.mockResolvedValue(existing);
    await renderEditor(existing);

    expect(screen.getByRole("button", { name: "Обновить оценку" })).toBeDisabled();
    await fireEvent(screen.getByLabelText("Энергия"), "slidingComplete", 0);
    await fireEvent.press(screen.getByRole("button", { name: "Обновить оценку" }));

    await waitFor(() =>
      expect(api.save).toHaveBeenCalledWith(
        expect.objectContaining({ values: { anxiety: 4, mood: 6, energy: 0 }, existing }),
      ),
    );
  });

  it("удаляет оценку, если все значения сброшены", async () => {
    api.remove.mockResolvedValue(undefined);
    await renderEditor({ ...existing, mood: null });

    await fireEvent.press(screen.getByRole("button", { name: "Сбросить: Тревожность" }));
    await fireEvent.press(screen.getByRole("button", { name: "Обновить оценку" }));

    await waitFor(() => expect(api.remove).toHaveBeenCalledWith({ date: "2026-10-18", slot: "DAY", existing: { ...existing, mood: null } }));
    expect(api.save).not.toHaveBeenCalled();
  });

  it("объясняет конфликт версий", async () => {
    api.save.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "x"));
    await renderEditor(existing);

    await fireEvent(screen.getByLabelText("Энергия"), "slidingComplete", 5);
    await fireEvent.press(screen.getByRole("button", { name: "Обновить оценку" }));

    expect(await screen.findByText(/уже изменена на другом устройстве/)).toBeTruthy();
  });

  it("показывает ошибку связи", async () => {
    api.save.mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x"));
    await renderEditor();

    await fireEvent(screen.getByLabelText("Настроение"), "slidingComplete", 5);
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить оценку" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });
});
