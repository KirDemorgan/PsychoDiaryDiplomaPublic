import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError, NETWORK_ERROR } from "@/api/errors";
import { sessionService } from "@/auth/session-service";

import { SignInScreen } from "./sign-in-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/auth/session-service", () => ({ sessionService: { signIn: jest.fn() } }));

const signIn = sessionService.signIn as jest.Mock;

async function fillAndSubmit(email: string, password: string) {
  await fireEvent.changeText(screen.getByLabelText("Email"), email);
  await fireEvent.changeText(screen.getByLabelText("Пароль"), password);
  await fireEvent.press(screen.getByRole("button", { name: "Войти" }));
}

describe("SignInScreen", () => {
  beforeEach(() => signIn.mockReset());

  it("отправляет введённые данные", async () => {
    signIn.mockResolvedValue(undefined);
    await render(<SignInScreen />);

    await fillAndSubmit("user@example.com", "secret");

    await waitFor(() => expect(signIn).toHaveBeenCalledWith({ email: "user@example.com", password: "secret" }));
  });

  it("не отправляет форму с ошибками валидации и показывает подсказки", async () => {
    await render(<SignInScreen />);

    await fillAndSubmit("not-email", "");

    expect(await screen.findByText("Проверьте формат email")).toBeTruthy();
    expect(screen.getByText("Введите пароль")).toBeTruthy();
    expect(signIn).not.toHaveBeenCalled();
  });

  it("показывает сообщение сервера при неверных данных", async () => {
    signIn.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS", "Неверные данные входа или сессия недоступна"));
    await render(<SignInScreen />);

    await fillAndSubmit("user@example.com", "wrong");

    expect(await screen.findByText("Неверные данные входа или сессия недоступна")).toBeTruthy();
  });

  it("показывает понятное сообщение при отсутствии связи", async () => {
    signIn.mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x"));
    await render(<SignInScreen />);

    await fillAndSubmit("user@example.com", "secret");

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
  });

  it("отправляет форму по клавише Go в поле пароля", async () => {
    signIn.mockResolvedValue(undefined);
    await render(<SignInScreen />);
    await fireEvent.changeText(screen.getByLabelText("Email"), "user@example.com");
    await fireEvent.changeText(screen.getByLabelText("Пароль"), "secret");

    await fireEvent(screen.getByLabelText("Пароль"), "submitEditing");

    await waitFor(() => expect(signIn).toHaveBeenCalledTimes(1));
  });
});
