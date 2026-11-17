import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { entriesApi } from "@/api/entries-api";
import { ApiError } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { DiaryScreen } from ".";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/api/entries-api", () => ({ entriesApi: { list: jest.fn() } }));

const api = entriesApi as jest.Mocked<typeof entriesApi>;
const router = jest.requireActual("@/test/mocks").router as { push: jest.Mock };

const summary = (id: string, text: string) => ({
  id,
  occurredAt: "2026-10-18T09:30:00Z",
  timeZone: "UTC",
  localDateInRequestedZone: null,
  textPreview: text,
  revision: 1,
  version: 0,
  reportVisible: true,
  confirmedTags: [],
  analysisStatus: null,
});

const pageOf = (items: ReturnType<typeof summary>[], totalPages = 1) => ({
  items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages,
});

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <DiaryScreen />
    </Wrapper>,
  );
}

describe("DiaryScreen", () => {
  beforeEach(() => {
    api.list.mockReset();
    router.push.mockReset();
  });

  it("показывает ленту записей", async () => {
    api.list.mockResolvedValue(pageOf([summary("1", "Первая запись"), summary("2", "Вторая запись")]));

    await renderScreen();

    expect(await screen.findByText("Первая запись")).toBeTruthy();
    expect(screen.getByText("Вторая запись")).toBeTruthy();
  });

  it("предлагает добавить первую запись, когда лента пуста", async () => {
    api.list.mockResolvedValue(pageOf([]));

    await renderScreen();

    expect(await screen.findByText("Записей пока нет. Добавьте первую.")).toBeTruthy();
  });

  it("открывает экран новой записи", async () => {
    api.list.mockResolvedValue(pageOf([]));
    await renderScreen();

    await fireEvent.press(screen.getByRole("button", { name: "Новая запись" }));

    expect(router.push).toHaveBeenCalledWith("/entry/new");
  });

  it("открывает календарь", async () => {
    api.list.mockResolvedValue(pageOf([]));
    await renderScreen();

    await fireEvent.press(screen.getByRole("button", { name: "Календарь" }));

    expect(router.push).toHaveBeenCalledWith("/diary/calendar");
  });

  it("ищет по тексту после паузы в наборе", async () => {
    api.list.mockResolvedValue(pageOf([summary("1", "Про сон")]));
    await renderScreen();
    await screen.findByText("Про сон");

    await fireEvent.changeText(screen.getByLabelText("Поиск по записям"), "сон");

    await waitFor(() => expect(api.list).toHaveBeenCalledWith({ q: "сон", page: 0 }));
  });

  it("сообщает, когда ничего не найдено", async () => {
    api.list.mockResolvedValueOnce(pageOf([summary("1", "Что-то")])).mockResolvedValue(pageOf([]));
    await renderScreen();
    await screen.findByText("Что-то");

    await fireEvent.changeText(screen.getByLabelText("Поиск по записям"), "нет такого");

    expect(await screen.findByText("Ничего не найдено.")).toBeTruthy();
  });

  it("показывает ошибку и повторяет загрузку", async () => {
    api.list.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(pageOf([summary("1", "После повтора")]));
    await renderScreen();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("После повтора")).toBeTruthy();
  });
});
