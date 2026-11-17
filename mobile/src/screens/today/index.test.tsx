import { fireEvent, render, screen } from "@testing-library/react-native";

import { authApi } from "@/api/auth-api";
import { ApiError } from "@/api/errors";
import { ratingsApi } from "@/api/ratings-api";
import { queryWrapper } from "@/test/query-wrapper";

import { TodayScreen } from ".";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/api/medications-api", () => ({ medicationsApi: { occurrences: jest.fn().mockResolvedValue({ items: [] }) } }));
jest.mock("@/api/auth-api", () => ({ authApi: { me: jest.fn() } }));
jest.mock("@/api/ratings-api", () => ({ ratingsApi: { listForRange: jest.fn().mockResolvedValue([]), save: jest.fn(), remove: jest.fn() } }));
jest.mock("@react-native-community/slider", () => jest.requireActual("@/test/mocks").sliderMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));

const me = authApi.me as jest.Mock;
void ratingsApi;

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <TodayScreen />
    </Wrapper>,
  );
}

describe("TodayScreen", () => {
  beforeEach(() => me.mockReset());

  it("приветствует пользователя по имени из профиля", async () => {
    me.mockResolvedValue({ displayName: "Анна" });

    await renderScreen();

    expect(await screen.findByText("Здравствуйте, Анна")).toBeTruthy();
    expect(screen.getByText("Как вы себя чувствуете?")).toBeTruthy();
    expect(screen.getByLabelText("Записать мысль")).toBeTruthy();
    expect(await screen.findByText("Приёмы сегодня")).toBeTruthy();
  });

  it("при ошибке показывает сообщение и позволяет повторить", async () => {
    me.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue({ displayName: "Анна" });

    await renderScreen();
    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();

    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Здравствуйте, Анна")).toBeTruthy();
  });
});
