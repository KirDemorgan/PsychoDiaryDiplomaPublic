import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Alert, type AlertButton } from "react-native";

import { ApiError } from "@/api/errors";
import { medicationsApi } from "@/api/medications-api";
import type { Medication, ScheduleVersion } from "@/api/medications-types";
import { queryWrapper } from "@/test/query-wrapper";

import { MedicationCard } from "./medication-card";
import { MedicationDetailScreen } from "./medication-detail-screen";
import { MedicationsScreen } from "./medications-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@react-native-community/datetimepicker", () => jest.requireActual("@/test/mocks").dateTimePickerMock);
jest.mock("expo-crypto", () => ({ randomUUID: () => "key" }));
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/medications-api", () => ({
  medicationsApi: {
    list: jest.fn(),
    get: jest.fn(),
    scheduleHistory: jest.fn(),
    createSchedule: jest.fn(),
    createIntake: jest.fn(),
    archive: jest.fn(),
    remove: jest.fn(),
  },
}));

const api = medicationsApi as jest.Mocked<typeof medicationsApi>;
const router = jest.requireActual("@/test/mocks").router as { push: jest.Mock; back: jest.Mock };

const schedule: ScheduleVersion = {
  id: "s1",
  medicationId: "m1",
  effectiveFrom: "2026-10-01T05:00:00Z",
  effectiveUntil: null,
  treatmentEndsOn: "2026-12-31",
  timeZone: "UTC",
  slots: [{ id: "sl1", localTime: "08:00:00", daysOfWeek: ["MONDAY", "FRIDAY"], dose: "1", unit: "TABLET" }],
  createdAt: "2026-10-01T05:00:00Z",
  medicationVersion: 1,
};

const medication: Medication = {
  id: "m1",
  name: "Препарат А",
  form: "таблетки",
  archivedAt: null,
  createdAt: "2026-10-01T05:00:00Z",
  updatedAt: "2026-10-01T05:00:00Z",
  version: 3,
  currentSchedule: schedule,
};

const withWrapper = async (ui: React.ReactElement) => {
  const Wrapper = queryWrapper();
  await render(<Wrapper>{ui}</Wrapper>);
};

const confirmAlert = (style: "destructive" | "default") =>
  jest.spyOn(Alert, "alert").mockImplementation((_t, _m, buttons?: AlertButton[]) => {
    const button = buttons?.find((item) => (style === "destructive" ? item.style === "destructive" : item.style !== "cancel"));
    button?.onPress?.();
  });

describe("MedicationCard", () => {
  it("показывает название, форму, слоты и конец курса", async () => {
    await render(<MedicationCard medication={medication} />);

    expect(screen.getByText("Препарат А")).toBeTruthy();
    expect(screen.getByText("таблетки")).toBeTruthy();
    expect(screen.getByText("08:00 · 1 таб. · Пн, Пт")).toBeTruthy();
    expect(screen.getByText(/Курс до 31 декабря 2026/)).toBeTruthy();
  });

  it("показывает отсутствие схемы и архив", async () => {
    await render(
      <MedicationCard
        medication={{ ...medication, form: null, currentSchedule: null, archivedAt: "2026-10-10T00:00:00Z" }}
      />,
    );

    expect(screen.getByText("Схема не задана")).toBeTruthy();
    expect(screen.getByText("В архиве")).toBeTruthy();
  });
});

describe("MedicationsScreen", () => {
  beforeEach(() => {
    api.list.mockReset();
    router.push.mockReset();
  });

  it("показывает список и открывает добавление", async () => {
    api.list.mockResolvedValue([medication]);
    await withWrapper(<MedicationsScreen />);

    expect(await screen.findByText("Препарат А")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Добавить препарат" }));

    expect(router.push).toHaveBeenCalledWith("/medications/new");
  });

  it("показывает пустое состояние и переключает архив", async () => {
    api.list.mockResolvedValue([]);
    await withWrapper(<MedicationsScreen />);

    expect(await screen.findByText("Препараты не добавлены.")).toBeTruthy();
    await fireEvent(screen.getByLabelText("Показать архивные"), "valueChange", true);

    expect(await screen.findByText("Архивных препаратов нет.")).toBeTruthy();
    expect(api.list).toHaveBeenLastCalledWith(true);
  });

  it("показывает ошибку и повторяет загрузку", async () => {
    api.list.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue([medication]);
    await withWrapper(<MedicationsScreen />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Препарат А")).toBeTruthy();
  });
});

describe("MedicationDetailScreen", () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => (fn as jest.Mock).mockReset());
    router.back.mockReset();
    api.get.mockResolvedValue(medication);
    api.scheduleHistory.mockResolvedValue([schedule]);
  });

  it("показывает текущую схему", async () => {
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("Препарат А")).toBeTruthy();
    expect(screen.getByText("08:00 · 1 таб. · Пн, Пт")).toBeTruthy();
    expect(screen.queryByText("История схем")).toBeNull();
  });

  it("показывает историю, когда версий несколько", async () => {
    api.scheduleHistory.mockResolvedValue([{ ...schedule, id: "s0" }, schedule]);
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("История схем")).toBeTruthy();
  });

  it("сообщает, что препарат не найден", async () => {
    api.get.mockRejectedValue(new ApiError(404, "RESOURCE_NOT_FOUND", "x"));
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("Препарат не найден.")).toBeTruthy();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.get.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(medication);
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Препарат А")).toBeTruthy();
  });

  it("сохраняет новую версию схемы с версией препарата", async () => {
    api.createSchedule.mockResolvedValue({} as never);
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Изменить схему" }));
    await fireEvent.changeText(screen.getByLabelText("Доза"), "2");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить схему" }));

    await waitFor(() => expect(api.createSchedule).toHaveBeenCalledWith("m1", 3, expect.objectContaining({ timeZone: "UTC" })));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Сохранить схему" })).toBeNull());
  });

  it("объясняет конфликт версий при сохранении схемы", async () => {
    api.createSchedule.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "x"));
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Изменить схему" }));
    await fireEvent.changeText(screen.getByLabelText("Доза"), "2");
    await fireEvent.press(screen.getByRole("button", { name: "Сохранить схему" }));

    expect(await screen.findByText(/изменён на другом устройстве/)).toBeTruthy();
  });

  it("отмена правки схемы возвращает просмотр", async () => {
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Изменить схему" }));
    await fireEvent.press(screen.getByRole("button", { name: "Отмена" }));

    expect(screen.getByRole("button", { name: "Изменить схему" })).toBeTruthy();
  });

  it("архивирует препарат после подтверждения", async () => {
    api.archive.mockResolvedValue({} as never);
    const alert = confirmAlert("default");
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Архивировать" }));

    await waitFor(() => expect(api.archive).toHaveBeenCalledWith("m1", 3));
    alert.mockRestore();
  });

  it("удаляет препарат после подтверждения и закрывает экран", async () => {
    api.remove.mockResolvedValue(undefined);
    const alert = confirmAlert("destructive");
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить" }));

    await waitFor(() => expect(api.remove).toHaveBeenCalledWith("m1", 3));
    await waitFor(() => expect(router.back).toHaveBeenCalled());
    alert.mockRestore();
  });

  it("показывает ошибку, если удалить не удалось", async () => {
    api.remove.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    const alert = confirmAlert("destructive");
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Удалить" }));

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    alert.mockRestore();
  });

  it("записывает внеплановый приём с текущим временем и единицей схемы", async () => {
    const api2 = api as jest.Mocked<typeof medicationsApi>;
    api2.createIntake.mockResolvedValue({} as never);
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Внеплановый приём" }));
    expect(screen.getByRole("button", { name: "Записать приём" })).toBeDisabled();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "0,5");
    await fireEvent.press(screen.getByRole("radio", { name: "мг" }));
    await fireEvent.press(screen.getByRole("button", { name: "Записать приём" }));

    await waitFor(() => expect(api2.createIntake).toHaveBeenCalled());
    expect(api2.createIntake.mock.calls[0][0]).toEqual({
      kind: "UNPLANNED",
      medicationId: "m1",
      status: "TAKEN",
      actualAt: expect.stringMatching(/Z$/),
      actualDose: 0.5,
      unit: "MG",
    });
    expect(await screen.findByText("Внеплановый приём записан.")).toBeTruthy();
  });

  it("внеплановый приём: по умолчанию единица схемы, отмена закрывает форму, ошибка показывается", async () => {
    const api2 = api as jest.Mocked<typeof medicationsApi>;
    api2.createIntake.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "x"));
    await withWrapper(<MedicationDetailScreen id="m1" />);

    await fireEvent.press(await screen.findByRole("button", { name: "Внеплановый приём" }));
    expect(screen.getByRole("radio", { name: "таб." })).toBeSelected();
    await fireEvent.changeText(screen.getByLabelText("Доза"), "1");
    await fireEvent.press(screen.getByRole("button", { name: "Записать приём" }));
    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();

    await fireEvent.press(screen.getByRole("button", { name: "Отмена" }));
    expect(screen.getByRole("button", { name: "Внеплановый приём" })).toBeTruthy();
  });

  it("для архивного препарата не предлагает менять схему и архивировать", async () => {
    api.get.mockResolvedValue({ ...medication, archivedAt: "2026-10-10T00:00:00Z" });
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText(/Препарат в архиве/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Изменить схему" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Архивировать" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Внеплановый приём" })).toBeNull();
  });

  it("только что сохранённую схему сразу показывает как текущую, без пояснений", async () => {
    const future = new Date(Date.now() + 30_000).toISOString();
    api.get.mockResolvedValue({ ...medication, currentSchedule: null });
    api.scheduleHistory.mockResolvedValue([{ ...schedule, effectiveFrom: future }]);
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("08:00 · 1 таб. · Пн, Пт")).toBeTruthy();
    expect(screen.queryByText("Схема не задана.")).toBeNull();
    expect(screen.queryByText(/начнёт действовать/)).toBeNull();
  });

  it("без схемы сообщает об этом", async () => {
    api.get.mockResolvedValue({ ...medication, currentSchedule: null });
    await withWrapper(<MedicationDetailScreen id="m1" />);

    expect(await screen.findByText("Схема не задана.")).toBeTruthy();
  });
});
