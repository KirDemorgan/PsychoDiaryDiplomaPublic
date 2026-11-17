import { render, screen } from "@testing-library/react-native";

import { ScreenTitle } from "./screen-title";

describe("ScreenTitle", () => {
  it("выводит заголовок как заголовок для VoiceOver", async () => {
    await render(<ScreenTitle>Динамика</ScreenTitle>);

    expect(screen.getByRole("header", { name: "Динамика" })).toBeTruthy();
  });
});
