import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { analysisApi } from "@/api/analysis-api";
import { entriesApi } from "@/api/entries-api";
import { settingsApi } from "@/api/settings-api";
import { ApiError, NETWORK_ERROR } from "@/api/errors";
import { useSessionStore } from "@/auth/session-store";
import { queryWrapper } from "@/test/query-wrapper";

import { NewEntryScreen } from "./new-entry-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/api/entries-api", () => ({ entriesApi: { create: jest.fn() } }));
jest.mock("@/api/analysis-api", () => ({ analysisApi: { start: jest.fn() } }));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn() } }));
jest.mock("@/features/entries/draft-store", () => ({
  loadDraft: jest.fn().mockResolvedValue(null),
  saveDraft: jest.fn().mockResolvedValue(undefined),
  clearDraft: jest.fn().mockResolvedValue(undefined),
}));
jest.mock("expo-crypto", () => ({ randomUUID: () => "draft-key" }));
const mockOnTranscript: { current: (text: string) => void } = { current: () => undefined };
jest.mock("./dictation-panel", () => ({
  DictationPanel: ({ onTranscript }: { onTranscript: (text: string) => void }) => {
    mockOnTranscript.current = onTranscript;
    return null;
  },
}));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));

const api = entriesApi as jest.Mocked<typeof entriesApi>;
const analysis = analysisApi as jest.Mocked<typeof analysisApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;
const router = jest.requireActual("@/test/mocks").router as { back: jest.Mock; replace: jest.Mock };
const { clearDraft } = jest.requireMock("@/features/entries/draft-store") as { clearDraft: jest.Mock };

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <NewEntryScreen />
    </Wrapper>,
  );
  await waitFor(() => expect(screen.getByLabelText("Что произошло сегодня?").props.editable).toBe(true));
}

describe("NewEntryScreen", () => {
  beforeEach(() => {
    api.create.mockReset();
    analysis.start.mockReset().mockResolvedValue({} as never);
    settings.get.mockReset().mockResolvedValue({ aiEnabled: false, version: 1 } as never);
    router.back.mockReset();
    router.replace.mockReset();
    clearDraft.mockClear();
    useSessionStore.setState({
      status: "signedIn",
      user: { id: "u1", email: "a@b.co", displayName: null, timeZone: "UTC", version: 0, dataRevision: 0 },
    });
  });

  it("не даёт сохранить пустой текст", async () => {
    await renderScreen();

    expect(screen.getByRole("button", { name: "Сохранить запись" })).toBeDisabled();
    expect(screen.getByText("0/5000")).toBeTruthy();
  });

  it("сохраняет запись с ключом черновика, очищает черновик и закрывает экран", async () => {
    api.create.mockResolvedValue({} as never);
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Что произошло сегодня?"), "Сегодня было спокойно");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить запись" }));

    await waitFor(() => expect(router.back).toHaveBeenCalled());
    expect(analysis.start).not.toHaveBeenCalled();
    expect(api.create).toHaveBeenCalledWith(
      {
        text: "Сегодня было спокойно",
        occurredAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+00:00$/),
        timeZone: "UTC",
      },
      "draft-key",
    );
    expect(clearDraft).toHaveBeenCalledWith("u1");
  });

  it("при включённом AI запускает анализ и открывает проверку тегов", async () => {
    settings.get.mockResolvedValue({ aiEnabled: true, version: 1 } as never);
    api.create.mockResolvedValue({ id: "e9", revision: 1 } as never);
    await renderScreen();
    await waitFor(() => expect(settings.get).toHaveBeenCalled());
    await act(async () => undefined);

    await fireEvent.changeText(screen.getByLabelText("Что произошло сегодня?"), "Плохо спал");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить запись" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith({ pathname: "/entry/review/[id]", params: { id: "e9" } }));
    expect(analysis.start).toHaveBeenCalledWith("e9", 1);
    expect(router.back).not.toHaveBeenCalled();
  });

  it("сбой запуска анализа не мешает открыть экран проверки, запись уже сохранена", async () => {
    settings.get.mockResolvedValue({ aiEnabled: true, version: 1 } as never);
    analysis.start.mockRejectedValue(new ApiError(503, "X", "x"));
    api.create.mockResolvedValue({ id: "e9", revision: 1 } as never);
    await renderScreen();
    await waitFor(() => expect(settings.get).toHaveBeenCalled());
    await act(async () => undefined);

    await fireEvent.changeText(screen.getByLabelText("Что произошло сегодня?"), "Текст");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить запись" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalled());
  });

  it("при ошибке сохраняет текст в черновике и объясняет это", async () => {
    api.create.mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x"));
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Что произошло сегодня?"), "Важный текст");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить запись" }));

    expect(await screen.findByText(/Текст остался в черновике на устройстве/)).toBeTruthy();
    expect(router.back).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Что произошло сегодня?").props.value).toBe("Важный текст");
  });

  it("добавляет расшифровку к тексту, не создавая запись автоматически", async () => {
    await renderScreen();

    await act(async () => mockOnTranscript.current("Сегодня плохо спал"));
    expect(screen.getByLabelText("Что произошло сегодня?").props.value).toBe("Сегодня плохо спал");

    await act(async () => mockOnTranscript.current("и тревожился"));

    expect(screen.getByLabelText("Что произошло сегодня?").props.value).toBe("Сегодня плохо спал и тревожился");
    expect(api.create).not.toHaveBeenCalled();
  });

  it("предупреждает о превышении лимита", async () => {
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Что произошло сегодня?"), "я".repeat(5001));

    expect(screen.getByText("5001/5000")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Сохранить запись" })).toBeDisabled();
  });
});
