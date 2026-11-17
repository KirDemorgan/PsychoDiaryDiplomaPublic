import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert, type AlertButton } from "react-native";

import { entriesApi } from "@/api/entries-api";
import type { EntryResponse } from "@/api/entries-types";
import { ApiError, NETWORK_ERROR } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { EntryDetailScreen } from "./entry-detail-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/api/entries-api", () => ({
  entriesApi: { get: jest.fn(), update: jest.fn(), remove: jest.fn() },
}));

const api = entriesApi as jest.Mocked<typeof entriesApi>;
const router = jest.requireActual("@/test/mocks").router as { back: jest.Mock };

const entry: EntryResponse = {
  id: "e1",
  text: "Исходный текст",
  occurredAt: "2026-10-18T09:30:00Z",
  timeZone: "UTC",
  revision: 1,
  version: 3,
  reportVisible: true,
  reviewComplete: false,
  tags: [],
  latestAnalysis: null,
  createdAt: "2026-10-18T09:30:00Z",
  updatedAt: "2026-10-18T09:30:00Z",
};

async function renderScreen() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <EntryDetailScreen id="e1" />
    </Wrapper>,
  );
}

describe("EntryDetailScreen", () => {
  beforeEach(() => {
    api.get.mockReset().mockResolvedValue(entry);
    api.update.mockReset();
    api.remove.mockReset();
    router.back.mockReset();
  });

  it("показывает текст и время записи", async () => {
    await renderScreen();

    expect(await screen.findByText("Исходный текст")).toBeTruthy();
    expect(screen.getByText(/09:30/)).toBeTruthy();
  });

  it("сообщает, что запись не найдена", async () => {
    api.get.mockRejectedValue(new ApiError(404, "RESOURCE_NOT_FOUND", "x"));

    await renderScreen();

    expect(await screen.findByText("Запись не найдена.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Повторить" })).toBeNull();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.get.mockRejectedValueOnce(new ApiError(0, NETWORK_ERROR, "x")).mockResolvedValue(entry);
    await renderScreen();

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Исходный текст")).toBeTruthy();
  });

  it("сохраняет правку текста с версией записи", async () => {
    api.update.mockResolvedValue({ ...entry, text: "Новый текст", version: 4 });
    await renderScreen();
    await fireEvent.press(await screen.findByRole("button", { name: "Изменить" }));

    expect(screen.getByRole("button", { name: "Сохранить" })).toBeDisabled();
    await fireEvent.changeText(screen.getByLabelText("Текст записи"), "Новый текст");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    await waitFor(() => expect(api.update).toHaveBeenCalledWith("e1", 3, { text: "Новый текст" }));
    await waitFor(() => expect(screen.queryByLabelText("Текст записи")).toBeNull());
  });

  it("переключает видимость записи в отчёте с версией записи", async () => {
    api.update.mockResolvedValue({ ...entry, reportVisible: false, version: 4 });
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Показывать в отчёте для врача"), "valueChange", false);

    await waitFor(() => expect(api.update).toHaveBeenCalledWith("e1", 3, { reportVisible: false }));
  });

  it("показывает ошибку, если видимость изменить не удалось", async () => {
    api.update.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "x"));
    await renderScreen();

    await fireEvent(await screen.findByLabelText("Показывать в отчёте для врача"), "valueChange", false);

    expect(await screen.findByText(/изменена на другом устройстве/)).toBeTruthy();
  });

  it("отмена правки возвращает просмотр", async () => {
    await renderScreen();
    await fireEvent.press(await screen.findByRole("button", { name: "Изменить" }));

    await fireEvent.press(screen.getByRole("button", { name: "Отмена" }));

    expect(screen.getByText("Исходный текст")).toBeTruthy();
    expect(api.update).not.toHaveBeenCalled();
  });

  it("объясняет конфликт версий", async () => {
    api.update.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "x"));
    await renderScreen();
    await fireEvent.press(await screen.findByRole("button", { name: "Изменить" }));

    await fireEvent.changeText(screen.getByLabelText("Текст записи"), "Правка");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(await screen.findByText(/изменена на другом устройстве/)).toBeTruthy();
  });

  it("удаляет запись после подтверждения и закрывает экран", async () => {
    api.remove.mockResolvedValue(undefined);
    const alert = jest.spyOn(Alert, "alert").mockImplementation((_title, _message, buttons?: AlertButton[]) => {
      buttons?.find((button) => button.style === "destructive")?.onPress?.();
    });
    await renderScreen();

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить" }));

    await waitFor(() => expect(api.remove).toHaveBeenCalledWith("e1", 3));
    await waitFor(() => expect(router.back).toHaveBeenCalled());
    alert.mockRestore();
  });

  it("показывает ошибку, если удалить не удалось", async () => {
    api.remove.mockRejectedValue(new ApiError(0, NETWORK_ERROR, "x"));
    const alert = jest.spyOn(Alert, "alert").mockImplementation((_title, _message, buttons?: AlertButton[]) => {
      buttons?.find((button) => button.style === "destructive")?.onPress?.();
    });
    await renderScreen();

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    expect(router.back).not.toHaveBeenCalled();
    alert.mockRestore();
  });
});
