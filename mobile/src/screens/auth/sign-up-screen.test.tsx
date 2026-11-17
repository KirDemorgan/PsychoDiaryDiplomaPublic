import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError } from "@/api/errors";
import { sessionService } from "@/auth/session-service";

import { SignUpScreen } from "./sign-up-screen";

jest.mock("@/auth/session-service", () => ({ sessionService: { signUp: jest.fn() } }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "Europe/Moscow" }));

const signUp = sessionService.signUp as jest.Mock;

async function fill(displayName: string, email: string, password: string) {
  await fireEvent.changeText(screen.getByLabelText("Имя или псевдоним (необязательно)"), displayName);
  await fireEvent.changeText(screen.getByLabelText("Email"), email);
  await fireEvent.changeText(screen.getByLabelText("Пароль"), password);
  await fireEvent.press(screen.getByRole("button", { name: "Создать аккаунт" }));
}

describe("SignUpScreen", () => {
  beforeEach(() => signUp.mockReset());

  it("регистрирует с часовым поясом устройства", async () => {
    signUp.mockResolvedValue(undefined);
    await render(<SignUpScreen />);

    await fill("Анна", "user@example.com", "correct-horse-battery");

    await waitFor(() =>
      expect(signUp).toHaveBeenCalledWith({
        email: "user@example.com",
        password: "correct-horse-battery",
        displayName: "Анна",
        timeZone: "Europe/Moscow",
      }),
    );
  });

  it("не передаёт пустое имя", async () => {
    signUp.mockResolvedValue(undefined);
    await render(<SignUpScreen />);

    await fill("", "user@example.com", "correct-horse-battery");

    await waitFor(() => expect(signUp).toHaveBeenCalledWith(expect.objectContaining({ displayName: undefined })));
  });

  it("требует пароль от 12 символов", async () => {
    await render(<SignUpScreen />);

    await fill("", "user@example.com", "short");

    expect(await screen.findByText("Минимум 12 символов")).toBeTruthy();
    expect(signUp).not.toHaveBeenCalled();
  });

  it("показывает ошибку сервера", async () => {
    signUp.mockRejectedValue(new ApiError(409, "DATA_CONFLICT", "Операция конфликтует с существующими данными"));
    await render(<SignUpScreen />);

    await fill("", "user@example.com", "correct-horse-battery");

    expect(await screen.findByText("Операция конфликтует с существующими данными")).toBeTruthy();
  });

  it("отправляет форму по клавише Go", async () => {
    signUp.mockResolvedValue(undefined);
    await render(<SignUpScreen />);
    await fireEvent.changeText(screen.getByLabelText("Email"), "user@example.com");
    await fireEvent.changeText(screen.getByLabelText("Пароль"), "correct-horse-battery");

    await fireEvent(screen.getByLabelText("Пароль"), "submitEditing");

    await waitFor(() => expect(signUp).toHaveBeenCalledTimes(1));
  });
});
