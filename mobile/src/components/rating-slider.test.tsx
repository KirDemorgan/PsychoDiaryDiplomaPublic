import { fireEvent, render, screen } from "@testing-library/react-native";

import { RatingSlider } from "./rating-slider";

jest.mock("@react-native-community/slider", () => jest.requireActual("@/test/mocks").sliderMock);

describe("RatingSlider", () => {
  it("показывает «Не указано» для пустого значения и не предлагает сброс", async () => {
    await render(<RatingSlider label="Тревожность" color="#888" value={null} onChange={jest.fn()} />);

    expect(screen.getByText("—")).toBeTruthy();
    expect(screen.getByLabelText("Тревожность").props.accessibilityValue).toEqual({ text: "Не указано" });
    expect(screen.queryByRole("button", { name: "Сбросить: Тревожность" })).toBeNull();
  });

  it("показывает значение и передаёт выбор пользователя", async () => {
    const onChange = jest.fn();
    await render(<RatingSlider label="Настроение" color="#888" value={0} onChange={onChange} />);

    expect(screen.getByText("0/10")).toBeTruthy();
    await fireEvent(screen.getByLabelText("Настроение"), "slidingComplete", 7);

    expect(onChange).toHaveBeenCalledWith(7);
  });

  it("сбрасывает значение в пустое", async () => {
    const onChange = jest.fn();
    await render(<RatingSlider label="Энергия" color="#888" value={5} onChange={onChange} />);

    await fireEvent.press(screen.getByRole("button", { name: "Сбросить: Энергия" }));

    expect(onChange).toHaveBeenCalledWith(null);
    expect(screen.getByLabelText("Энергия").props.accessibilityValue).toEqual({ text: "5 из 10" });
  });
});
