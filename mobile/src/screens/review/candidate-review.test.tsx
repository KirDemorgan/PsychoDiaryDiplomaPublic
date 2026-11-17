import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { analysisApi } from "@/api/analysis-api";
import { ApiError } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { CandidateReview } from "./candidate-review";
import { baseEntry, candidates } from "./review-fixtures";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-symbols", () => jest.requireActual("@/test/mocks").symbolsMock);
jest.mock("@/api/analysis-api", () => ({ analysisApi: { submitReview: jest.fn() } }));

const api = analysisApi as jest.Mocked<typeof analysisApi>;
const router = jest.requireActual("@/test/mocks").router as { back: jest.Mock };

async function renderReview(list = candidates) {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <CandidateReview entry={baseEntry} candidates={list} />
    </Wrapper>,
  );
}

describe("CandidateReview", () => {
  beforeEach(() => {
    api.submitReview.mockReset();
    router.back.mockReset();
  });

  it("показывает запись и предложенные теги отмеченными", async () => {
    await renderReview();

    expect(screen.getByText("Предложенные теги")).toBeTruthy();
    expect(screen.getByRole("checkbox", { name: "Недостаток сна" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Тревога" })).toBeChecked();
    expect(screen.getByRole("button", { name: "Подтвердить теги" })).toBeTruthy();
  });

  it("подтверждает принятые теги с версией записи и датой", async () => {
    api.submitReview.mockResolvedValue({} as never);
    await renderReview();

    await fireEvent.press(screen.getByRole("checkbox", { name: "Тревога" }));
    await fireEvent.press(screen.getByRole("button", { name: "Подтвердить теги" }));

    await waitFor(() => expect(router.back).toHaveBeenCalled());
    expect(api.submitReview).toHaveBeenCalledWith(
      "e1",
      5,
      {
        revision: 2,
        complete: true,
        decisions: [
          { candidateId: "c1", action: "ACCEPT", appliesToLocalDate: "2026-10-18" },
          { candidateId: "c2", action: "REJECT" },
        ],
        manualTags: [],
      },
      undefined,
    );
  });

  it("позволяет завершить без тегов, если все отклонены", async () => {
    await renderReview();

    await fireEvent.press(screen.getByRole("checkbox", { name: "Недостаток сна" }));
    await fireEvent.press(screen.getByRole("checkbox", { name: "Тревога" }));

    expect(screen.getByRole("button", { name: "Завершить без тегов" })).toBeTruthy();
  });

  it("исправляет тег через редактор", async () => {
    api.submitReview.mockResolvedValue({} as never);
    await renderReview();

    await fireEvent.press(screen.getByRole("button", { name: "Изменить: Тревога" }));
    expect(screen.getByRole("button", { name: "Подтвердить теги" })).toBeDisabled();
    await fireEvent.press(screen.getByRole("radio", { name: "Усталость" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Нет" }));
    await fireEvent.press(screen.getByRole("button", { name: "Готово" }));

    expect(screen.getByText(/изменено/)).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Подтвердить теги" }));

    await waitFor(() => expect(api.submitReview).toHaveBeenCalled());
    const body = api.submitReview.mock.calls[0][2];
    expect(body.decisions[1]).toMatchObject({
      candidateId: "c2",
      action: "EDIT",
      replacement: { labelCode: "FATIGUE", presence: "NEGATED", quote: "тревожился", start: 29, end: 39 },
    });
  });

  it("отмена редактирования ничего не меняет", async () => {
    await renderReview();

    await fireEvent.press(screen.getByRole("button", { name: "Изменить: Недостаток сна" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Кофеин" }));
    await fireEvent.press(screen.getByRole("button", { name: "Отмена" }));

    expect(screen.getByRole("checkbox", { name: "Недостаток сна" })).toBeChecked();
    expect(screen.queryByText(/изменено/)).toBeNull();
  });

  it("повторное открытие редактора показывает уже исправленные значения", async () => {
    await renderReview();
    await fireEvent.press(screen.getByRole("button", { name: "Изменить: Тревога" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Усталость" }));
    await fireEvent.press(screen.getByRole("button", { name: "Готово" }));

    await fireEvent.press(screen.getByRole("button", { name: "Изменить: Усталость" }));

    expect(screen.getByRole("radio", { name: "Усталость" })).toBeSelected();
  });

  it("объясняет устаревшую редакцию", async () => {
    api.submitReview.mockRejectedValue(new ApiError(409, "STALE_REVISION", "x"));
    await renderReview();

    await fireEvent.press(screen.getByRole("button", { name: "Подтвердить теги" }));

    expect(await screen.findByText(/Запись изменилась/)).toBeTruthy();
    expect(router.back).not.toHaveBeenCalled();
  });

  it("показывает другие ошибки сервера", async () => {
    api.submitReview.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await renderReview();

    await fireEvent.press(screen.getByRole("button", { name: "Подтвердить теги" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });

  it("при отсутствии кандидатов предлагает завершить проверку", async () => {
    await renderReview([]);

    expect(screen.getByText(/AI не нашёл подходящих признаков/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Завершить без тегов" })).toBeTruthy();
  });
});
