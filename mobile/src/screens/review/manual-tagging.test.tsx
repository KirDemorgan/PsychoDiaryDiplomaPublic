import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { analysisApi } from "@/api/analysis-api";
import { ApiError } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { ManualTagging } from "./manual-tagging";
import { baseEntry } from "./review-fixtures";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-symbols", () => jest.requireActual("@/test/mocks").symbolsMock);
jest.mock("@/api/analysis-api", () => ({ analysisApi: { submitReview: jest.fn() } }));

const api = analysisApi as jest.Mocked<typeof analysisApi>;
const router = jest.requireActual("@/test/mocks").router as { back: jest.Mock };

const select = async (start: number, end: number) =>
  fireEvent(screen.getByLabelText("Текст записи для выделения"), "selectionChange", {
    nativeEvent: { selection: { start, end } },
  });

async function renderIt() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <ManualTagging entry={baseEntry} />
    </Wrapper>,
  );
}

describe("ManualTagging", () => {
  beforeEach(() => {
    api.submitReview.mockReset();
    router.back.mockReset();
  });

  it("пока фрагмент не выбран, добавить тег нельзя", async () => {
    await renderIt();

    expect(screen.getByText("Фрагмент не выбран.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Добавить тег" })).toBeDisabled();
  });

  it("добавляет тег с цитатой и UTF-16 позициями выделения", async () => {
    api.submitReview.mockResolvedValue({} as never);
    await renderIt();

    await select(0, 13);
    expect(screen.getByText("Выбрано: «Почти не спал»")).toBeTruthy();
    await fireEvent.press(screen.getByRole("radio", { name: "Недостаток сна" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Нет" }));
    await fireEvent.press(screen.getByRole("button", { name: "Добавить тег" }));
    expect(screen.getByText(/«Почти не спал» · Нет · Сейчас/)).toBeTruthy();
    expect(screen.getByText("Фрагмент не выбран.")).toBeTruthy();

    await fireEvent.press(screen.getByRole("button", { name: "Подтвердить теги" }));

    await waitFor(() => expect(router.back).toHaveBeenCalled());
    expect(api.submitReview).toHaveBeenCalledWith(
      "e1",
      5,
      {
        revision: 2,
        complete: true,
        decisions: [],
        manualTags: [
          {
            labelCode: "SLEEP_DEFICIT",
            quote: "Почти не спал",
            start: 0,
            end: 13,
            presence: "NEGATED",
            temporal: "CURRENT",
            subject: "SELF",
            appliesToLocalDate: "2026-10-18",
          },
        ],
      },
      undefined,
    );
  });

  it("убирает добавленный тег и позволяет завершить без тегов", async () => {
    api.submitReview.mockResolvedValue({} as never);
    await renderIt();
    await select(29, 39);
    await fireEvent.press(screen.getByRole("button", { name: "Добавить тег" }));

    await fireEvent.press(screen.getByRole("button", { name: "Убрать" }));
    await fireEvent.press(screen.getByRole("button", { name: "Завершить без тегов" }));

    await waitFor(() => expect(api.submitReview).toHaveBeenCalled());
    expect(api.submitReview.mock.calls[0][2].manualTags).toEqual([]);
  });

  it("объясняет устаревшую редакцию и другие ошибки", async () => {
    api.submitReview.mockRejectedValueOnce(new ApiError(409, "STALE_REVISION", "x"));
    await renderIt();

    await fireEvent.press(screen.getByRole("button", { name: "Завершить без тегов" }));
    expect(await screen.findByText(/Запись изменилась/)).toBeTruthy();

    api.submitReview.mockRejectedValueOnce(new ApiError(0, "NETWORK_ERROR", "x"));
    await fireEvent.press(screen.getByRole("button", { name: "Завершить без тегов" }));
    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    expect(router.back).not.toHaveBeenCalled();
  });

  it("игнорирует выделение из одних пробелов", async () => {
    await renderIt();

    await select(14, 15);

    expect(screen.getByText("Фрагмент не выбран.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Добавить тег" })).toBeDisabled();
  });
});
