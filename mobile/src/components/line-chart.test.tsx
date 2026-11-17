import { fireEvent, render, screen } from "@testing-library/react-native";

import { LineChart } from "./line-chart";

const layout = { nativeEvent: { layout: { width: 320, height: 220, x: 0, y: 0 } } };

describe("LineChart", () => {
  it("описывает график для VoiceOver и рисуется после измерения ширины", async () => {
    await render(
      <LineChart
        values={[3, null, 6, 7]}
        labels={["12.10", "13.10", "14.10", "15.10"]}
        color="#8B6CF0"
        threshold={7}
        accessibilityLabel="Тревожность за 4 дня"
      />,
    );

    const chart = screen.getByRole("image", { name: "Тревожность за 4 дня" });
    await fireEvent(chart, "layout", layout);

    expect(screen.getByRole("image", { name: "Тревожность за 4 дня" })).toBeTruthy();
  });

  it("рисуется без порога и без данных", async () => {
    await render(<LineChart values={[null, null]} labels={["a", "b"]} color="#18B39F" accessibilityLabel="Нет данных" />);

    await fireEvent(screen.getByRole("image", { name: "Нет данных" }), "layout", layout);

    expect(screen.getByRole("image", { name: "Нет данных" })).toBeTruthy();
  });
});
