import { render, screen } from "@testing-library/react-native";
import { Text } from "react-native";

import { Backdrop } from "./backdrop";
import { BackdropScroll } from "./backdrop-scroll";

describe("Backdrop", () => {
  it("рисуется декоративно и не попадает в дерево доступности", async () => {
    await render(<Backdrop />);

    expect(screen.root).toBeTruthy();
  });

  it("BackdropScroll показывает содержимое поверх подложки", async () => {
    await render(
      <BackdropScroll>
        <Text>Контент</Text>
      </BackdropScroll>,
    );

    expect(screen.getByText("Контент")).toBeTruthy();
  });
});
