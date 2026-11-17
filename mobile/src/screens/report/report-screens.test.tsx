import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert, type AlertButton } from "react-native";

import { ApiError } from "@/api/errors";
import { reportsApi } from "@/api/reports-api";
import type { Report, ReportSummary } from "@/api/reports-types";
import { settingsApi } from "@/api/settings-api";
import { shareFile } from "@/lib/share-file";
import { queryWrapper } from "@/test/query-wrapper";

import { ReportBuilderScreen } from "./report-builder-screen";
import { ReportDetailScreen } from "./report-detail-screen";
import { ReportsScreen } from "./reports-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/lib/share-file", () => ({
  shareFile: jest.fn(),
  PDF_FILE: { mimeType: "application/pdf", uti: "com.adobe.pdf" },
}));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn() } }));
jest.mock("@/api/reports-api", () => ({
  reportsApi: { list: jest.fn(), get: jest.fn(), create: jest.fn(), cancel: jest.fn(), remove: jest.fn(), pdf: jest.fn() },
}));

const api = reportsApi as jest.Mocked<typeof reportsApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;
const share = shareFile as jest.Mock;
const router = jest.requireActual("@/test/mocks").router as { push: jest.Mock; replace: jest.Mock; back: jest.Mock };

const summary = (overrides: Partial<ReportSummary> = {}): ReportSummary => ({
  id: "r1",
  from: "2026-09-19",
  to: "2026-10-18",
  timeZone: "UTC",
  status: "READY",
  stale: false,
  createdAt: "2026-10-18T10:00:00Z",
  version: 2,
  overviewMode: "NONE",
  warnings: [],
  ...overrides,
});

const report = (overrides: Partial<Report> = {}): Report =>
  ({
    ...summary(),
    selection: {},
    overview: null,
    previewData: { statistics: null, ratings: [1, 2, 3], entries: [1], medications: [], comparisons: [] },
    errorCode: null,
    ...overrides,
  }) as Report;

const withWrapper = async (ui: React.ReactElement) => {
  const Wrapper = queryWrapper();
  await render(<Wrapper>{ui}</Wrapper>);
};

const confirmDelete = () =>
  jest.spyOn(Alert, "alert").mockImplementation((_t, _m, buttons?: AlertButton[]) => {
    buttons?.find((button) => button.style === "destructive")?.onPress?.();
  });

describe("ReportBuilderScreen", () => {
  beforeEach(() => {
    api.create.mockReset().mockResolvedValue(report({ id: "r9", status: "QUEUED" }));
    settings.get.mockReset().mockResolvedValue({ aiEnabled: false, version: 1 } as never);
    router.replace.mockReset();
  });

  it("создаёт отчёт с выбранным составом и переходит к нему", async () => {
    await withWrapper(<ReportBuilderScreen />);

    await fireEvent.press(screen.getByRole("radio", { name: "14 дней" }));
    await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним в отчёте (необязательно)"), "Анна");
    await fireEvent(screen.getByLabelText("Записи дневника"), "valueChange", false);
    await fireEvent.press(screen.getByRole("radio", { name: "Тревога" }));
    await fireEvent.press(screen.getByRole("button", { name: "Сформировать отчёт" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith({ pathname: "/report/[id]", params: { id: "r9" } }));
    const body = api.create.mock.calls[0][0];
    expect(body).toMatchObject({
      timeZone: "UTC",
      displayName: "Анна",
      sections: ["RATINGS", "TAGS", "MEDICATIONS"],
      entrySelection: "NONE",
      medicationSelection: "ALL",
      comparisonTagCodes: ["ANXIETY"],
    });
    expect(api.create.mock.calls[0][1]).toBe("key");
  });

  it("не даёт включить AI-обзор, пока AI выключен", async () => {
    await withWrapper(<ReportBuilderScreen />);

    expect(await screen.findByText("AI-обзор недоступен: AI выключен в настройках.")).toBeTruthy();
    expect(screen.getByLabelText("AI-обзор наблюдений").props.disabled).toBe(true);
  });

  it("включает AI-обзор в запрос, когда AI разрешён", async () => {
    settings.get.mockResolvedValue({ aiEnabled: true, version: 1 } as never);
    await withWrapper(<ReportBuilderScreen />);

    await waitFor(() => expect(screen.getByLabelText("AI-обзор наблюдений").props.disabled).toBeFalsy());
    await fireEvent(screen.getByLabelText("AI-обзор наблюдений"), "valueChange", true);
    await fireEvent.press(screen.getByRole("button", { name: "Сформировать отчёт" }));

    await waitFor(() => expect(api.create).toHaveBeenCalled());
    expect(api.create.mock.calls[0][0].sections).toContain("AI_OVERVIEW");
  });

  it("скрывает выбор тегов, если теги выключены, и снимает выбранный тег повторным нажатием", async () => {
    await withWrapper(<ReportBuilderScreen />);

    await fireEvent.press(screen.getByRole("radio", { name: "Тревога" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Тревога" }));
    expect(screen.getByRole("radio", { name: "Тревога" })).not.toBeSelected();

    await fireEvent(screen.getByLabelText("Подтверждённые теги и сравнение"), "valueChange", false);
    expect(screen.queryByText("Сравнить дни с тегом и без")).toBeNull();
  });

  it("показывает ошибку создания", async () => {
    api.create.mockRejectedValue(new ApiError(403, "FORBIDDEN", "AI выключен или нет согласия"));
    await withWrapper(<ReportBuilderScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Сформировать отчёт" }));

    expect(await screen.findByText("AI выключен или нет согласия")).toBeTruthy();
    expect(router.replace).not.toHaveBeenCalled();
  });
});

describe("ReportsScreen", () => {
  beforeEach(() => {
    api.list.mockReset();
    router.push.mockReset();
  });

  it("показывает список отчётов и открывает создание", async () => {
    api.list.mockResolvedValue([summary(), summary({ id: "r2", stale: true, status: "FAILED" })]);
    await withWrapper(<ReportsScreen />);

    expect(await screen.findByText("Готов")).toBeTruthy();
    expect(screen.getByText("Ошибка · устарел")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Новый отчёт" }));
    expect(router.push).toHaveBeenCalledWith("/report/new");
  });

  it("показывает пустое состояние", async () => {
    api.list.mockResolvedValue([]);
    await withWrapper(<ReportsScreen />);

    expect(await screen.findByText("Отчётов пока нет.")).toBeTruthy();
  });

  it("при ошибке позволяет повторить", async () => {
    api.list.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue([summary()]);
    await withWrapper(<ReportsScreen />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Готов")).toBeTruthy();
  });
});

describe("ReportDetailScreen", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => (fn as jest.Mock).mockReset());
    share.mockReset().mockResolvedValue(undefined);
    router.back.mockReset();
    api.get.mockResolvedValue(report());
    api.pdf.mockResolvedValue(new ArrayBuffer(4));
  });

  it("показывает состав готового отчёта", async () => {
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText("Оценок: 3")).toBeTruthy();
    expect(screen.getByText("Записей: 1")).toBeTruthy();
    expect(screen.getByText("Препаратов: 0")).toBeTruthy();
    expect(screen.queryByText("Включить AI-обзор в PDF")).toBeNull();
  });

  it("скачивает PDF и открывает системный Share без AI-обзора, если он не включён", async () => {
    await withWrapper(<ReportDetailScreen id="r1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Поделиться PDF" }));

    await waitFor(() => expect(share).toHaveBeenCalledWith(
        expect.any(ArrayBuffer),
        "diary-report-2026-09-19-2026-10-18.pdf",
        expect.objectContaining({ mimeType: "application/pdf" }),
      ));
    expect(api.pdf).toHaveBeenCalledWith("r1", false);
  });

  it("показывает AI-обзор и позволяет исключить его из PDF", async () => {
    api.get.mockResolvedValue(
      report({
        overviewMode: "AI",
        overview: {
          title: "AI-обзор",
          claims: [{ id: "c1", text: "В дни с недостатком сна тревожность была выше.", factIds: ["F1"] }],
          limitations: ["Связь, а не причина."],
          provenance: { mode: "AI", modelId: "m" },
        },
      }),
    );
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText("В дни с недостатком сна тревожность была выше.")).toBeTruthy();
    expect(screen.getByText("AI-обзор проверен программой")).toBeTruthy();
    expect(screen.getByText("Связь, а не причина.")).toBeTruthy();

    await fireEvent(screen.getByLabelText("Включить AI-обзор в PDF"), "valueChange", false);
    await fireEvent.press(screen.getByRole("button", { name: "Поделиться PDF" }));

    await waitFor(() => expect(api.pdf).toHaveBeenCalledWith("r1", false));
  });

  it("запрашивает AI-обзор в PDF по умолчанию, когда он есть", async () => {
    api.get.mockResolvedValue(report({ overviewMode: "AI", overview: { title: "t", claims: [], limitations: [], provenance: { mode: "AI", modelId: null } } }));
    await withWrapper(<ReportDetailScreen id="r1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Поделиться PDF" }));

    await waitFor(() => expect(api.pdf).toHaveBeenCalledWith("r1", true));
  });

  it("показывает замечания отчёта", async () => {
    api.get.mockResolvedValue(report({ warnings: ["MODEL_NOT_CONFIGURED"] }));
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText("MODEL_NOT_CONFIGURED")).toBeTruthy();
  });

  it("для устаревшего отчёта предупреждает и блокирует отправку", async () => {
    api.get.mockResolvedValue(report({ stale: true }));
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText(/Данные изменились после создания отчёта/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Поделиться PDF" })).toBeDisabled();
  });

  it("показывает ход подготовки и позволяет отменить", async () => {
    api.get.mockResolvedValue(report({ status: "RUNNING" }));
    api.cancel.mockResolvedValue(report({ status: "CANCELLED" }));
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText("Отчёт готовится…")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Удалить отчёт" })).toBeNull();
    await fireEvent.press(screen.getByRole("button", { name: "Отменить" }));

    await waitFor(() => expect(api.cancel).toHaveBeenCalledWith("r1"));
  });

  it.each([
    ["FAILED", "Не удалось подготовить отчёт. Создайте новый."],
    ["CANCELLED", "Отчёт отменён."],
  ] as const)("сообщает о статусе %s", async (status, text) => {
    api.get.mockResolvedValue(report({ status }));
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText(text)).toBeTruthy();
  });

  it("показывает ошибку скачивания PDF", async () => {
    api.pdf.mockRejectedValue(new ApiError(409, "REPORT_STALE", "Источники изменились, создайте новый отчёт"));
    await withWrapper(<ReportDetailScreen id="r1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Поделиться PDF" }));

    expect(await screen.findByText("Источники изменились, создайте новый отчёт")).toBeTruthy();
    expect(share).not.toHaveBeenCalled();
  });

  it("удаляет отчёт после подтверждения и закрывает экран", async () => {
    api.remove.mockResolvedValue(undefined);
    const alert = confirmDelete();
    await withWrapper(<ReportDetailScreen id="r1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить отчёт" }));

    await waitFor(() => expect(api.remove).toHaveBeenCalledWith("r1", 2));
    await waitFor(() => expect(router.back).toHaveBeenCalled());
    alert.mockRestore();
  });

  it("показывает ошибку удаления", async () => {
    api.remove.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    const alert = confirmDelete();
    await withWrapper(<ReportDetailScreen id="r1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить отчёт" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    alert.mockRestore();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(report());
    await withWrapper(<ReportDetailScreen id="r1" />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Оценок: 3")).toBeTruthy();
  });
});
