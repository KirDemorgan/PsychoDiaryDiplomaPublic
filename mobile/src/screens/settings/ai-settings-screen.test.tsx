import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError } from "@/api/errors";
import { settingsApi } from "@/api/settings-api";
import { speechApi } from "@/api/speech-api";
import { queryWrapper } from "@/test/query-wrapper";

import { AiSettingsScreen } from "./ai-settings-screen";

jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn(), update: jest.fn() } }));
jest.mock("@/api/speech-api", () => ({ speechApi: { consent: jest.fn(), updateConsent: jest.fn() } }));

const api = settingsApi as jest.Mocked<typeof settingsApi>;
const speech = speechApi as jest.Mocked<typeof speechApi>;

const settings = (aiEnabled: boolean) => ({ aiEnabled, version: 3 }) as never;

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <AiSettingsScreen />
    </Wrapper>,
  );
}

describe("AiSettingsScreen", () => {
  beforeEach(() => {
    api.get.mockReset();
    api.update.mockReset();
    speech.consent.mockReset().mockResolvedValue({ enabled: false, version: 7 } as never);
    speech.updateConsent.mockReset().mockResolvedValue({ enabled: true, version: 8 } as never);
  });

  it("включает голосовой ввод отдельным согласием с версией", async () => {
    api.get.mockResolvedValue(settings(true));
    await renderScreen();

    await waitFor(() => expect(screen.getByLabelText("Голосовой ввод").props.disabled).toBeFalsy());
    await fireEvent(screen.getByLabelText("Голосовой ввод"), "valueChange", true);

    await waitFor(() => expect(speech.updateConsent).toHaveBeenCalledWith(7, true));
  });

  it("не позволяет включить голос при выключенном AI", async () => {
    api.get.mockResolvedValue(settings(false));
    await renderScreen();

    expect(await screen.findByText("Сначала включите AI-анализ записей.")).toBeTruthy();
    expect(screen.getByLabelText("Голосовой ввод").props.disabled).toBe(true);
  });

  it("не вызывает согласие, пока оно не загружено", async () => {
    api.get.mockResolvedValue(settings(true));
    speech.consent.mockReturnValue(new Promise(() => undefined) as never);
    await renderScreen();

    expect((await screen.findByLabelText("Голосовой ввод")).props.disabled).toBe(true);
    await fireEvent(screen.getByLabelText("Голосовой ввод"), "valueChange", true);

    expect(speech.updateConsent).not.toHaveBeenCalled();
  });

  it("показывает ошибку сохранения согласия на голос", async () => {
    api.get.mockResolvedValue(settings(true));
    speech.updateConsent.mockRejectedValue(new ApiError(409, "CONFLICT", "Конфликт версий"));
    await renderScreen();

    await waitFor(() => expect(screen.getByLabelText("Голосовой ввод").props.disabled).toBeFalsy());
    await fireEvent(screen.getByLabelText("Голосовой ввод"), "valueChange", true);

    expect(await screen.findByText("Конфликт версий")).toBeTruthy();
  });

  it("включает AI с согласием текущей версии", async () => {
    api.get.mockResolvedValue(settings(false));
    api.update.mockResolvedValue(settings(true));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("AI-анализ записей"), "valueChange", true);

    await waitFor(() =>
      expect(api.update).toHaveBeenCalledWith(3, { aiConsentVersion: "diary-ai-v1", aiEnabled: true }),
    );
  });

  it("выключает AI без повторного согласия", async () => {
    api.get.mockResolvedValue(settings(true));
    api.update.mockResolvedValue(settings(false));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("AI-анализ записей"), "valueChange", false);

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(3, { aiEnabled: false }));
  });

  it("показывает ошибку сохранения", async () => {
    api.get.mockResolvedValue(settings(false));
    api.update.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("AI-анализ записей"), "valueChange", true);

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(settings(true));
    await renderScreen();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByLabelText("AI-анализ записей")).toBeTruthy();
  });
});
