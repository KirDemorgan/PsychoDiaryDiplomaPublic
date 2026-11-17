import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { analysisApi } from "@/api/analysis-api";
import { entriesApi } from "@/api/entries-api";
import { ApiError } from "@/api/errors";
import { settingsApi } from "@/api/settings-api";
import { queryWrapper } from "@/test/query-wrapper";

import { analysis, baseEntry } from "./review-fixtures";
import { ReviewScreen } from "./review-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-symbols", () => jest.requireActual("@/test/mocks").symbolsMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));
jest.mock("@/api/entries-api", () => ({ entriesApi: { get: jest.fn() } }));
jest.mock("@/api/analysis-api", () => ({ analysisApi: { get: jest.fn(), start: jest.fn() } }));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn() } }));

const entries = entriesApi as jest.Mocked<typeof entriesApi>;
const analyses = analysisApi as jest.Mocked<typeof analysisApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;

const aiOn = { aiEnabled: true, version: 1 } as never;

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <ReviewScreen entryId="e1" />
    </Wrapper>,
  );
}

describe("ReviewScreen", () => {
  beforeEach(() => {
    entries.get.mockReset();
    analyses.get.mockReset();
    analyses.start.mockReset();
    settings.get.mockReset().mockResolvedValue(aiOn);
  });

  it("при ошибке загрузки записи позволяет повторить", async () => {
    entries.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(baseEntry);
    await renderScreen();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByRole("button", { name: "Запустить AI-анализ" })).toBeTruthy();
  });

  it("без анализа предлагает запустить его для текущей редакции", async () => {
    entries.get.mockResolvedValue(baseEntry);
    analyses.start.mockResolvedValue(analysis({ status: "QUEUED", result: null }));
    await renderScreen();

    await fireEvent.press(await screen.findByRole("button", { name: "Запустить AI-анализ" }));

    await waitFor(() => expect(analyses.start).toHaveBeenCalledWith("e1", 2, undefined));
  });

  it("показывает ход анализа, пока он в очереди", async () => {
    entries.get.mockResolvedValue({ ...baseEntry, latestAnalysis: analysis({ status: "RUNNING", result: null }) });
    analyses.get.mockResolvedValue(analysis({ status: "RUNNING", result: null }));
    await renderScreen();

    expect(await screen.findByText("AI ищет теги в записи…")).toBeTruthy();
  });

  it("показывает кандидатов успешного анализа", async () => {
    entries.get.mockResolvedValue({ ...baseEntry, latestAnalysis: analysis() });
    analyses.get.mockResolvedValue(analysis());
    await renderScreen();

    expect(await screen.findByRole("checkbox", { name: "Недостаток сна" })).toBeChecked();
  });

  it("после сбоя анализа сообщает об этом и предлагает повторить", async () => {
    const failed = analysis({ status: "FAILED", result: null, errorCode: "MODEL_ERROR" });
    entries.get.mockResolvedValue({ ...baseEntry, latestAnalysis: failed });
    analyses.get.mockResolvedValue(failed);
    await renderScreen();

    expect(await screen.findByText(/Не удалось получить теги от AI/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Повторить анализ" })).toBeTruthy();
  });

  it("анализ другой редакции не показывается, предлагается новый запуск", async () => {
    const old = analysis({ revision: 1 });
    entries.get.mockResolvedValue({ ...baseEntry, latestAnalysis: old });
    analyses.get.mockResolvedValue(old);
    await renderScreen();

    expect(await screen.findByRole("button", { name: "Запустить AI-анализ" })).toBeTruthy();
    expect(screen.queryByText(/Не удалось получить теги/)).toBeNull();
  });

  it("позволяет перейти к ручной разметке, в том числе при выключенном AI", async () => {
    settings.get.mockResolvedValue({ aiEnabled: false, version: 1 } as never);
    entries.get.mockResolvedValue(baseEntry);
    await renderScreen();

    await fireEvent.press(await screen.findByRole("button", { name: "Разметить вручную" }));

    expect(screen.getByText("Ручная разметка")).toBeTruthy();
    expect(screen.getByLabelText("Текст записи для выделения")).toBeTruthy();
  });

  it("при выключенном AI отправляет в настройки и не предлагает запуск", async () => {
    settings.get.mockResolvedValue({ aiEnabled: false, version: 1 } as never);
    entries.get.mockResolvedValue(baseEntry);
    await renderScreen();

    expect(await screen.findByText(/AI выключен/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Запустить AI-анализ" })).toBeNull();
  });

  it("показывает ошибку запуска анализа", async () => {
    entries.get.mockResolvedValue(baseEntry);
    analyses.start.mockRejectedValue(new ApiError(403, "FORBIDDEN", "Действие запрещено"));
    await renderScreen();

    await fireEvent.press(await screen.findByRole("button", { name: "Запустить AI-анализ" }));

    expect(await screen.findByText("Действие запрещено")).toBeTruthy();
  });

  it("для подтверждённой записи показывает итоговые теги", async () => {
    entries.get.mockResolvedValue({
      ...baseEntry,
      reviewComplete: true,
      tags: [
        {
          id: "t1",
          labelCode: "ANXIETY",
          category: "STATE",
          quote: "тревожился",
          start: 29,
          end: 39,
          presence: "PRESENT",
          temporal: "CURRENT",
          subject: "SELF",
          appliesToLocalDate: "2026-10-18",
          origin: "AI",
        },
      ],
    });
    await renderScreen();

    expect(await screen.findByText("Теги подтверждены")).toBeTruthy();
    expect(screen.getByText(/Тревога · Есть · «тревожился»/)).toBeTruthy();
  });

  it("подтверждение без тегов показывает пустой итог", async () => {
    entries.get.mockResolvedValue({ ...baseEntry, reviewComplete: true, tags: [] });
    await renderScreen();

    expect(await screen.findByText("Без тегов.")).toBeTruthy();
  });
});
