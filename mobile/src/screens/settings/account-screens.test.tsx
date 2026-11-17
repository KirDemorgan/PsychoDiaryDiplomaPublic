import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert, type AlertButton } from "react-native";

import { accountApi } from "@/api/account-api";
import { ApiError } from "@/api/errors";
import { exportsApi } from "@/api/exports-api";
import { settingsApi } from "@/api/settings-api";
import { sessionService } from "@/auth/session-service";
import { useSessionStore } from "@/auth/session-store";
import { shareFile } from "@/lib/share-file";
import { queryWrapper } from "@/test/query-wrapper";

import { AppearanceScreen } from "./appearance-screen";
import { DataScreen } from "./data-screen";
import { ProfileScreen } from "./profile-screen";

jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "Asia/Yekaterinburg" }));
jest.mock("@/lib/share-file", () => ({ shareFile: jest.fn(), ZIP_FILE: { mimeType: "application/zip", uti: "z" } }));
jest.mock("@/auth/session-service", () => ({ sessionService: { signOut: jest.fn() } }));
jest.mock("@/api/account-api", () => ({ accountApi: { updateProfile: jest.fn(), deleteAccount: jest.fn() } }));
jest.mock("@/api/exports-api", () => ({ exportsApi: { create: jest.fn(), file: jest.fn() } }));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn(), update: jest.fn() } }));

const account = accountApi as jest.Mocked<typeof accountApi>;
const exportsMock = exportsApi as jest.Mocked<typeof exportsApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;
const share = shareFile as jest.Mock;
const signOut = sessionService.signOut as jest.Mock;

const user = { id: "u1", email: "a@b.co", displayName: "Анна", timeZone: "Europe/Moscow", version: 5, dataRevision: 0 };

const renderIn = async (ui: React.ReactElement) => {
  const Wrapper = queryWrapper();
  await render(<Wrapper>{ui}</Wrapper>);
};

const confirmDestructive = () =>
  jest.spyOn(Alert, "alert").mockImplementation((_t, _m, buttons?: AlertButton[]) => {
    buttons?.find((button) => button.style === "destructive")?.onPress?.();
  });

describe("ProfileScreen", () => {
  beforeEach(() => {
    account.updateProfile.mockReset();
    useSessionStore.setState({ status: "signedIn", user });
  });

  it("сохраняет новое имя с версией профиля и обновляет пользователя", async () => {
    account.updateProfile.mockResolvedValue({ ...user, displayName: "Мария", version: 6 });
    await renderIn(<ProfileScreen />);

    expect(screen.getByRole("button", { name: "Сохранить" })).toBeDisabled();
    await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним"), "  Мария ");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    await waitFor(() => expect(account.updateProfile).toHaveBeenCalledWith(5, { displayName: "Мария" }));
    expect(await screen.findByText("Сохранено.")).toBeTruthy();
    expect(useSessionStore.getState().user?.displayName).toBe("Мария");
  });

  it("пустое имя очищает поле на сервере", async () => {
    account.updateProfile.mockResolvedValue({ ...user, displayName: null, version: 6 });
    await renderIn(<ProfileScreen />);

    await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним"), "   ");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    await waitFor(() => expect(account.updateProfile).toHaveBeenCalledWith(5, { displayName: null }));
  });

  it("не даёт сохранить слишком длинное имя", async () => {
    await renderIn(<ProfileScreen />);

    await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним"), "я".repeat(101));

    expect(screen.getByText("Не более 100 символов")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Сохранить" })).toBeDisabled();
  });

  it("предлагает сменить часовой пояс профиля на пояс устройства", async () => {
    account.updateProfile.mockResolvedValue({ ...user, timeZone: "Asia/Yekaterinburg", version: 6 });
    await renderIn(<ProfileScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Использовать пояс устройства (Asia/Yekaterinburg)" }));

    await waitFor(() => expect(account.updateProfile).toHaveBeenCalledWith(5, { timeZone: "Asia/Yekaterinburg" }));
  });

  it("не предлагает смену пояса, если он совпадает", async () => {
    useSessionStore.setState({ user: { ...user, timeZone: "Asia/Yekaterinburg" } });
    await renderIn(<ProfileScreen />);

    expect(screen.queryByText(/Использовать пояс устройства/)).toBeNull();
  });

  it("показывает ошибку сохранения", async () => {
    account.updateProfile.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "Ресурс изменён; получите актуальную версию"));
    await renderIn(<ProfileScreen />);

    await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним"), "Новое");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(await screen.findByText("Ресурс изменён; получите актуальную версию")).toBeTruthy();
  });

  it("ничего не рисует без пользователя", async () => {
    useSessionStore.setState({ status: "signedOut", user: null });
    await renderIn(<ProfileScreen />);

    expect(screen.queryByLabelText("Имя или псевдоним")).toBeNull();
  });
});

describe("AppearanceScreen", () => {
  beforeEach(() => {
    settings.get.mockReset().mockResolvedValue({ theme: null, version: 2 } as never);
    settings.update.mockReset().mockResolvedValue({ theme: "DARK", version: 3 } as never);
  });

  it("показывает системную тему по умолчанию и сохраняет выбор", async () => {
    await renderIn(<AppearanceScreen />);

    expect(await screen.findByRole("radio", { name: "Системная" })).toBeSelected();
    await fireEvent.press(screen.getByRole("radio", { name: "Тёмная" }));

    await waitFor(() => expect(settings.update).toHaveBeenCalledWith(2, { theme: "DARK" }));
  });

  it("показывает ошибку сохранения", async () => {
    settings.update.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await renderIn(<AppearanceScreen />);

    await fireEvent.press(await screen.findByRole("radio", { name: "Светлая" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    settings.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue({ theme: "LIGHT", version: 2 } as never);
    await renderIn(<AppearanceScreen />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByRole("radio", { name: "Светлая" })).toBeSelected();
  });
});

describe("DataScreen", () => {
  beforeEach(() => {
    exportsMock.create.mockReset().mockResolvedValue({ id: "x1" } as never);
    exportsMock.file.mockReset().mockResolvedValue(new ArrayBuffer(8));
    share.mockReset().mockResolvedValue(undefined);
    account.deleteAccount.mockReset();
    signOut.mockReset().mockResolvedValue(undefined);
  });

  it("выгружает данные архивом и открывает системный Share", async () => {
    await renderIn(<DataScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Скачать мои данные" }));

    await waitFor(() => expect(share).toHaveBeenCalledWith(expect.any(ArrayBuffer), "diary-export.zip", expect.anything()));
    expect(exportsMock.file).toHaveBeenCalledWith("x1");
  });

  it("показывает ошибку экспорта", async () => {
    exportsMock.create.mockRejectedValue(new ApiError(429, "TOO_MANY", "x"));
    await renderIn(<DataScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Скачать мои данные" }));

    expect(await screen.findByText("Слишком много попыток. Подождите немного и повторите.")).toBeTruthy();
    expect(share).not.toHaveBeenCalled();
  });

  it("блокирует удаление без пароля и подтверждения", async () => {
    await renderIn(<DataScreen />);
    const button = screen.getByRole("button", { name: "Удалить аккаунт" });

    expect(button).toBeDisabled();
    await fireEvent.changeText(screen.getByLabelText("Текущий пароль"), "secret");
    expect(button).toBeDisabled();
    await fireEvent(screen.getByLabelText("Я понимаю, что это необратимо"), "valueChange", true);
    expect(screen.getByRole("button", { name: "Удалить аккаунт" })).toBeEnabled();
  });

  it("удаляет аккаунт после подтверждения и завершает сессию", async () => {
    account.deleteAccount.mockResolvedValue(undefined);
    const alert = confirmDestructive();
    await renderIn(<DataScreen />);

    await fireEvent.changeText(screen.getByLabelText("Текущий пароль"), "secret");
    await fireEvent(screen.getByLabelText("Я понимаю, что это необратимо"), "valueChange", true);
    await fireEvent.press(screen.getByRole("button", { name: "Удалить аккаунт" }));

    await waitFor(() => expect(account.deleteAccount).toHaveBeenCalledWith("secret"));
    await waitFor(() => expect(signOut).toHaveBeenCalled());
    alert.mockRestore();
  });

  it("при неверном пароле показывает ошибку и не завершает сессию", async () => {
    account.deleteAccount.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS", "Неверные данные входа или сессия недоступна"));
    const alert = confirmDestructive();
    await renderIn(<DataScreen />);

    await fireEvent.changeText(screen.getByLabelText("Текущий пароль"), "wrong");
    await fireEvent(screen.getByLabelText("Я понимаю, что это необратимо"), "valueChange", true);
    await fireEvent.press(screen.getByRole("button", { name: "Удалить аккаунт" }));

    expect(await screen.findByText("Неверные данные входа или сессия недоступна")).toBeTruthy();
    expect(signOut).not.toHaveBeenCalled();
    alert.mockRestore();
  });
});
