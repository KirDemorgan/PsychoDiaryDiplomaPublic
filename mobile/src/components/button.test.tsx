import { fireEvent, render, screen } from "@testing-library/react-native";

import { Button } from "./button";

describe("Button", () => {
  it("вызывает обработчик нажатия", async () => {
    const onPress = jest.fn();
    await render(<Button title="Войти" onPress={onPress} />);

    await fireEvent.press(screen.getByRole("button", { name: "Войти" }));

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it("в состоянии загрузки блокируется и скрывает заголовок", async () => {
    const onPress = jest.fn();
    await render(<Button title="Войти" onPress={onPress} loading />);

    expect(screen.queryByText("Войти")).toBeNull();
    await fireEvent.press(screen.getByRole("button"));
    expect(onPress).not.toHaveBeenCalled();
  });

  it.each(["secondary", "destructive"] as const)("рисует вариант %s", async (variant) => {
    await render(<Button title="Выйти" variant={variant} />);

    expect(screen.getByText("Выйти")).toBeTruthy();
  });
});
