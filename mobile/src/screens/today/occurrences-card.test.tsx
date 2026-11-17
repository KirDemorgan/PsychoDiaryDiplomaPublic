import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { ApiError } from "@/api/errors";
import { medicationsApi } from "@/api/medications-api";
import type { Occurrence } from "@/api/medications-types";
import { queryWrapper } from "@/test/query-wrapper";

import { OccurrencesCard } from "./occurrences-card";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/medications-api", () => ({
  medicationsApi: { occurrences: jest.fn(), createIntake: jest.fn(), removeIntake: jest.fn() },
}));

const api = medicationsApi as jest.Mocked<typeof medicationsApi>;

const occurrence = (overrides: Partial<Occurrence> = {}): Occurrence => ({
  occurrenceId: "o1",
  medicationId: "m1",
  medicationName: "Препарат А",
  scheduleVersionId: "s1",
  scheduleSlotId: "sl1",
  plannedAt: "2026-10-18T08:00:00Z",
  plannedDose: "1.0",
  unit: "TABLET",
  timeZone: "UTC",
  status: "NO_DATA",
  intake: null,
  ...overrides,
});

async function renderCard() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <OccurrencesCard />
    </Wrapper>,
  );
}

describe("OccurrencesCard", () => {
  beforeEach(() => {
    api.occurrences.mockReset();
    api.createIntake.mockReset();
    api.removeIntake.mockReset();
  });

  it("сообщает, что приёмов на сегодня нет", async () => {
    api.occurrences.mockResolvedValue({ items: [] } as never);
    await renderCard();

    expect(await screen.findByText("На сегодня приёмов нет.")).toBeTruthy();
  });

  it("показывает плановые приёмы со временем, дозой и статусом", async () => {
    api.occurrences.mockResolvedValue({ items: [occurrence()] } as never);
    await renderCard();

    expect(await screen.findByText("08:00 · Препарат А")).toBeTruthy();
    expect(screen.getByText("1 таб. · Нет отметки")).toBeTruthy();
  });

  it("отмечает приём как принятый с плановой дозой и текущим временем", async () => {
    api.occurrences.mockResolvedValue({ items: [occurrence()] } as never);
    api.createIntake.mockResolvedValue({} as never);
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Принял" }));

    await waitFor(() => expect(api.createIntake).toHaveBeenCalled());
    expect(api.createIntake.mock.calls[0][0]).toEqual({
      kind: "PLANNED",
      occurrenceId: "o1",
      status: "TAKEN",
      actualAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T.*Z$/),
      actualDose: 1,
      unit: "TABLET",
    });
  });

  it("отмечает приём как пропущенный без фактических времени и дозы", async () => {
    api.occurrences.mockResolvedValue({ items: [occurrence()] } as never);
    api.createIntake.mockResolvedValue({} as never);
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Пропустил" }));

    await waitFor(() =>
      expect(api.createIntake.mock.calls[0][0]).toEqual({ kind: "PLANNED", occurrenceId: "o1", status: "SKIPPED" }),
    );
  });

  it("снимает отметку по версии приёма", async () => {
    api.occurrences.mockResolvedValue({
      items: [occurrence({ status: "TAKEN", intake: { id: "i1", version: 2 } as never })],
    } as never);
    api.removeIntake.mockResolvedValue(undefined);
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Снять отметку" }));

    await waitFor(() => expect(api.removeIntake).toHaveBeenCalledWith("i1", 2));
  });

  it("не снимает отметку, если у приёма нет записи", async () => {
    api.occurrences.mockResolvedValue({ items: [occurrence({ status: "SKIPPED", intake: null })] } as never);
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Снять отметку" }));

    expect(api.removeIntake).not.toHaveBeenCalled();
  });

  it("показывает ошибку отметки", async () => {
    api.occurrences.mockResolvedValue({ items: [occurrence()] } as never);
    api.createIntake.mockRejectedValue(new ApiError(409, "INTAKE_ALREADY_EXISTS", "Для плановой дозы уже есть отметка"));
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Принял" }));

    expect(await screen.findByText("Для плановой дозы уже есть отметка")).toBeTruthy();
  });

  it("показывает ошибку при снятии отметки", async () => {
    api.occurrences.mockResolvedValue({
      items: [occurrence({ status: "TAKEN", intake: { id: "i1", version: 2 } as never })],
    } as never);
    api.removeIntake.mockRejectedValue(new ApiError(412, "VERSION_CONFLICT", "Ресурс изменён; получите актуальную версию"));
    await renderCard();

    await fireEvent.press(await screen.findByRole("button", { name: "Снять отметку" }));

    expect(await screen.findByText("Ресурс изменён; получите актуальную версию")).toBeTruthy();
  });

  it("при ошибке загрузки позволяет повторить", async () => {
    api.occurrences.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue({ items: [] } as never);
    await renderCard();

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("На сегодня приёмов нет.")).toBeTruthy();
  });
});
