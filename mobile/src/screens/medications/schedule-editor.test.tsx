import { fireEvent, render, screen } from "@testing-library/react-native";

import { ScheduleEditor } from "./schedule-editor";

jest.mock("@react-native-community/datetimepicker", () => jest.requireActual("@/test/mocks").dateTimePickerMock);
let mockUuid = 0;
jest.mock("expo-crypto", () => ({ randomUUID: () => `uuid-${++mockUuid}` }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));

async function renderEditor(onSubmit = jest.fn(), serverError: string | null = null) {
  await render(<ScheduleEditor submitLabel="Сохранить" busy={false} serverError={serverError} onSubmit={onSubmit} />);
  return onSubmit;
}

describe("ScheduleEditor", () => {
  beforeEach(() => {
    mockUuid = 0;
  });

  it("не отправляет схему без дозы и подсказывает исправить", async () => {
    const onSubmit = await renderEditor();

    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(screen.getByText("Укажите дозу числом больше нуля")).toBeTruthy();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("отправляет схему с выбранными днями, дозой и единицей", async () => {
    const onSubmit = await renderEditor();

    await fireEvent.changeText(screen.getByLabelText("Доза"), "0,5");
    await fireEvent.press(screen.getByRole("radio", { name: "мг" }));
    await fireEvent.press(screen.getByRole("radio", { name: "Вс" }));
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const schedule = onSubmit.mock.calls[0][0];
    expect(schedule.slots).toEqual([
      { localTime: "09:00", daysOfWeek: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"], dose: 0.5, unit: "MG" },
    ]);
    expect(schedule.timeZone).toBe("UTC");
    expect(schedule.treatmentEndsOn).toBeNull();
    expect(schedule.effectiveFrom).toMatch(/\+00:00$/);
  });

  it("начало схемы немного в будущем, чтобы сервер не счёл её заданной задним числом", async () => {
    const onSubmit = await renderEditor();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "1");

    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    const start = new Date(onSubmit.mock.calls[0][0].effectiveFrom).getTime();
    expect(start).toBeGreaterThan(Date.now() + 15_000);
    expect(start).toBeLessThanOrEqual(Date.now() + 31_000);
  });

  it("запрещает убрать все дни", async () => {
    const onSubmit = await renderEditor();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "1");

    for (const day of ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]) {
      await fireEvent.press(screen.getByRole("radio", { name: day }));
    }
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(screen.getByText("Выберите дни приёма")).toBeTruthy();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("добавляет и убирает время приёма, оставляя минимум одно", async () => {
    await renderEditor();
    expect(screen.queryByRole("button", { name: "Убрать приём" })).toBeNull();

    await fireEvent.press(screen.getByRole("button", { name: "Добавить время приёма" }));
    expect(screen.getAllByLabelText(/Время приёма/)).toHaveLength(2);

    await fireEvent.press(screen.getAllByRole("button", { name: "Убрать приём" })[0]);
    expect(screen.getAllByLabelText(/Время приёма/)).toHaveLength(1);
  });

  it("добавляет последний день курса, когда курс ограничен", async () => {
    const onSubmit = await renderEditor();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "1");

    await fireEvent(screen.getByLabelText("Ограничить курс датой"), "valueChange", true);
    await fireEvent(screen.getByLabelText("Последний день курса"), "change", {}, new Date(2026, 10, 5, 12));
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(onSubmit.mock.calls[0][0].treatmentEndsOn).toBe("2026-11-05");
  });

  it("показывает ошибку сервера", async () => {
    await renderEditor(jest.fn(), "Препарат архивирован");

    expect(screen.getByText("Препарат архивирован")).toBeTruthy();
  });

  it("меняет время приёма", async () => {
    const onSubmit = await renderEditor();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "1");

    await fireEvent(screen.getByLabelText("Время приёма 1"), "change", {}, new Date(2026, 9, 18, 7, 30));
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить" }));

    expect(onSubmit.mock.calls[0][0].slots[0].localTime).toBe("07:30");
  });
});
