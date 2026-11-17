import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert } from "react-native";

import { ApiError } from "@/api/errors";
import { sessionService } from "@/auth/session-service";
import { useSessionStore } from "@/auth/session-store";

import { SettingsScreen } from ".";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("expo-symbols", () => jest.requireActual("@/test/mocks").symbolsMock);
jest.mock("@/auth/session-service", () => ({ sessionService: { signOut: jest.fn() } }));

const signOut = sessionService.signOut as jest.Mock;

describe("SettingsScreen", () => {
  beforeEach(() => {
    signOut.mockReset();
    useSessionStore.setState({
      status: "signedIn",
      user: { id: "u1", email: "a@b.co", displayName: null, timeZone: "Europe/Moscow", version: 0, dataRevision: 0 },
    });
  });

  it("показывает пользователя и разделы настроек", async () => {
    await render(<SettingsScreen />);

    expect(screen.getByText("Без имени")).toBeTruthy();
    expect(screen.getByText("a@b.co")).toBeTruthy();
    for (const title of ["Профиль", "Препараты", "AI и согласия", "Напоминания", "Внешний вид", "Экспорт и удаление данных", "Знакомство с приложением"]) {
      expect(screen.getByText(title)).toBeTruthy();
    }
  });

  it("не показывает блок пользователя без сессии", async () => {
    useSessionStore.setState({ status: "signedOut", user: null });

    await render(<SettingsScreen />);

    expect(screen.queryByText("Без имени")).toBeNull();
  });

  it("выходит из аккаунта", async () => {
    signOut.mockResolvedValue(undefined);
    await render(<SettingsScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Выйти" }));

    await waitFor(() => expect(signOut).toHaveBeenCalledTimes(1));
  });

  it("сообщает, если выйти не удалось", async () => {
    signOut.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    const alert = jest.spyOn(Alert, "alert").mockImplementation(() => undefined);
    await render(<SettingsScreen />);

    await fireEvent.press(screen.getByRole("button", { name: "Выйти" }));

    await waitFor(() => expect(alert).toHaveBeenCalledWith("Не удалось выйти", expect.stringContaining("Нет связи")));
    alert.mockRestore();
  });
});
