import { fireEvent, render, screen } from "@testing-library/react-native";

import { Chip } from "./chip";
import { HighlightedText } from "./highlighted-text";

describe("Chip", () => {
  it("отмечает выбранность и сообщает о нажатии", async () => {
    const onPress = jest.fn();
    await render(
      <>
        <Chip label="Тревога" selected onPress={onPress} />
        <Chip label="Сон" selected={false} color="#123456" onPress={onPress} />
      </>,
    );

    expect(screen.getByRole("radio", { name: "Тревога" })).toBeSelected();
    expect(screen.getByRole("radio", { name: "Сон" })).not.toBeSelected();
    await fireEvent.press(screen.getByRole("radio", { name: "Сон" }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});

describe("HighlightedText", () => {
  it("выводит весь текст, подсвечивая цитаты", async () => {
    await render(
      <HighlightedText
        text="Почти не спал. Тревожился."
        spans={[{ id: "a", start: 0, end: 13, color: "#B79CF2" }]}
      />,
    );

    expect(screen.getByText("Почти не спал")).toBeTruthy();
    expect(screen.getByText(/Тревожился/)).toBeTruthy();
  });

  it("показывает текст без выделений, когда диапазонов нет", async () => {
    await render(<HighlightedText text="Просто текст" spans={[]} />);

    expect(screen.getByText("Просто текст")).toBeTruthy();
  });
});
