import { fireEvent, render, screen } from "@testing-library/react-native";

import { TextField } from "./text-field";

describe("TextField", () => {
  it("показывает подпись и передаёт ввод", async () => {
    const onChangeText = jest.fn();
    await render(<TextField label="Email" onChangeText={onChangeText} />);

    await fireEvent.changeText(screen.getByLabelText("Email"), "a@b.co");

    expect(screen.getByText("Email")).toBeTruthy();
    expect(onChangeText).toHaveBeenCalledWith("a@b.co");
  });

  it("показывает ошибку как оповещение", async () => {
    await render(<TextField label="Email" error="Введите email" />);

    expect(screen.getByRole("alert")).toHaveTextContent("Введите email");
  });

  it("без ошибки оповещения нет", async () => {
    await render(<TextField label="Email" />);

    expect(screen.queryByRole("alert")).toBeNull();
  });
});
