import { render, screen } from "@testing-library/react-native";

import { AppText } from "./app-text";
import { Card } from "./card";
import { ComingSoon } from "./coming-soon";
import { Screen } from "./screen";

describe("простые компоненты", () => {
  it("AppText выводит текст в обоих вариантах цвета", async () => {
    await render(
      <>
        <AppText>Основной</AppText>
        <AppText secondary variant="caption">
          Вторичный
        </AppText>
      </>,
    );

    expect(screen.getByText("Основной")).toBeTruthy();
    expect(screen.getByText("Вторичный")).toBeTruthy();
  });

  it("Card и Screen рендерят содержимое", async () => {
    await render(
      <Screen>
        <Card>
          <AppText>Внутри</AppText>
        </Card>
      </Screen>,
    );

    expect(screen.getByText("Внутри")).toBeTruthy();
  });

  it("ComingSoon показывает заголовок и описание по умолчанию", async () => {
    await render(<ComingSoon title="Профиль" />);

    expect(screen.getByText("Профиль")).toBeTruthy();
    expect(screen.getByText(/появится/)).toBeTruthy();
  });

  it("ComingSoon принимает своё описание", async () => {
    await render(<ComingSoon title="Дневник" description="Лента записей" />);

    expect(screen.getByText("Лента записей")).toBeTruthy();
  });
});
