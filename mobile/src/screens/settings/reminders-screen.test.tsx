import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Linking } from "react-native";

import { ApiError } from "@/api/errors";
import { settingsApi } from "@/api/settings-api";
import { readPermission, requestPermission } from "@/features/reminders/scheduler";
import { queryWrapper } from "@/test/query-wrapper";

import { RemindersScreen } from "./reminders-screen";

jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn(), update: jest.fn() } }));
jest.mock("@/features/reminders/scheduler", () => ({ readPermission: jest.fn(), requestPermission: jest.fn() }));

const api = settingsApi as jest.Mocked<typeof settingsApi>;
const read = readPermission as jest.Mock;
const request = requestPermission as jest.Mock;

const settings = (overrides = {}) => ({ remindersEnabled: false, neutralNotificationText: true, version: 4, ...overrides }) as never;

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <RemindersScreen />
    </Wrapper>,
  );
}

describe("RemindersScreen", () => {
  beforeEach(() => {
    api.get.mockReset().mockResolvedValue(settings());
    api.update.mockReset().mockResolvedValue(settings());
    read.mockReset().mockResolvedValue("undetermined");
    request.mockReset().mockResolvedValue("granted");
  });

  it("включает напоминания после выдачи разрешения", async () => {
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Напоминания о приёмах"), "valueChange", true);

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(4, { remindersEnabled: true }));
    expect(request).toHaveBeenCalled();
  });

  it("не включает напоминания и подсказывает открыть настройки iOS, если разрешение отклонено", async () => {
    request.mockResolvedValue("denied");
    const open = jest.spyOn(Linking, "openSettings").mockResolvedValue(undefined);
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Напоминания о приёмах"), "valueChange", true);

    expect(await screen.findByText("Уведомления запрещены в настройках iOS.")).toBeTruthy();
    expect(api.update).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByRole("button", { name: "Открыть настройки iOS" }));
    expect(open).toHaveBeenCalled();
    open.mockRestore();
  });

  it("выключает напоминания без запроса разрешения", async () => {
    api.get.mockResolvedValue(settings({ remindersEnabled: true }));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Напоминания о приёмах"), "valueChange", false);

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(4, { remindersEnabled: false }));
    expect(request).not.toHaveBeenCalled();
  });

  it("переключает нейтральный текст", async () => {
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Нейтральный текст"), "valueChange", false);

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(4, { neutralNotificationText: false }));
  });

  it("показывает уже запрещённое разрешение сразу", async () => {
    read.mockResolvedValue("denied");
    await renderScreen();

    expect(await screen.findByText("Уведомления запрещены в настройках iOS.")).toBeTruthy();
  });

  it("показывает ошибку сохранения", async () => {
    api.update.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Нейтральный текст"), "valueChange", false);

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(settings());
    await renderScreen();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByLabelText("Нейтральный текст")).toBeTruthy();
  });
});
