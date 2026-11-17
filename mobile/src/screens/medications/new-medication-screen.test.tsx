import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError } from "@/api/errors";
import { medicationsApi } from "@/api/medications-api";
import { queryWrapper } from "@/test/query-wrapper";

import { NewMedicationScreen } from "./new-medication-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@react-native-community/datetimepicker", () => jest.requireActual("@/test/mocks").dateTimePickerMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/medications-api", () => ({ medicationsApi: { create: jest.fn(), createSchedule: jest.fn() } }));

const api = medicationsApi as jest.Mocked<typeof medicationsApi>;
const router = jest.requireActual("@/test/mocks").router as { back: jest.Mock };

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <NewMedicationScreen />
    </Wrapper>,
  );
}

async function fillSchedule() {
  await fireEvent.changeText(screen.getByLabelText("Доза"), "1");
}

describe("NewMedicationScreen", () => {
  beforeEach(() => {
    api.create.mockReset();
    api.createSchedule.mockReset();
    router.back.mockReset();
  });

  it("требует название", async () => {
    await renderScreen();
    await fillSchedule();

    await fireEvent.press(screen.getByRole("button", { name: "Сохранить препарат" }));

    expect(await screen.findByText("Введите название препарата")).toBeTruthy();
    expect(api.create).not.toHaveBeenCalled();
  });

  it("создаёт препарат и схему, затем закрывает экран", async () => {
    api.create.mockResolvedValue({ id: "m1", version: 0 } as never);
    api.createSchedule.mockResolvedValue({} as never);
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Название"), "  Препарат  ");
    await fireEvent.changeText(screen.getByLabelText("Форма (необязательно)"), "таблетки");
    await fillSchedule();
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить препарат" }));

    await waitFor(() => expect(router.back).toHaveBeenCalled());
    expect(api.create).toHaveBeenCalledWith({ name: "Препарат", form: "таблетки" }, "key");
    expect(api.createSchedule).toHaveBeenCalledWith("m1", 0, expect.objectContaining({ timeZone: "UTC" }));
  });

  it("пустую форму не отправляет", async () => {
    api.create.mockResolvedValue({ id: "m1", version: 0 } as never);
    api.createSchedule.mockResolvedValue({} as never);
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Название"), "Препарат");
    await fillSchedule();
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить препарат" }));

    await waitFor(() => expect(api.create).toHaveBeenCalledWith({ name: "Препарат", form: undefined }, "key"));
  });

  it("показывает ошибку сервера и не закрывает экран", async () => {
    api.create.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await renderScreen();

    await fireEvent.changeText(screen.getByLabelText("Название"), "Препарат");
    await fillSchedule();
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить препарат" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    expect(router.back).not.toHaveBeenCalled();
  });
});
