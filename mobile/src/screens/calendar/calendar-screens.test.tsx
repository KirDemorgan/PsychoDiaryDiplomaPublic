import { fireEvent, render, screen } from "@testing-library/react-native";

import { calendarApi } from "@/api/calendar-api";
import type { CalendarDay, DayResponse } from "@/api/calendar-types";
import { ApiError } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { CalendarScreen } from "./calendar-screen";
import { DayScreen } from "./day-screen";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/calendar-api", () => ({ calendarApi: { calendar: jest.fn(), day: jest.fn() } }));

const api = calendarApi as jest.Mocked<typeof calendarApi>;
const router = jest.requireActual("@/test/mocks").router as { push: jest.Mock };

const rating = (overrides = {}) => ({ anxiety: 4, mood: null, energy: 6, ...overrides }) as never;

const dayOf = (overrides: Partial<CalendarDay> = {}): CalendarDay => ({
  date: "2026-10-18",
  ratings: {},
  entryCount: 0,
  reviewedEntryCount: 0,
  pendingReviewCount: 0,
  confirmedPresentTagCodes: [],
  ambiguousTagCodes: [],
  medication: null,
  ...overrides,
});

const renderIn = async (ui: React.ReactElement) => {
  const Wrapper = queryWrapper();
  await render(<Wrapper>{ui}</Wrapper>);
};

describe("CalendarScreen", () => {
  beforeEach(() => {
    jest.useFakeTimers({ now: new Date("2026-10-18T09:00:00Z"), doNotFake: ["nextTick", "setImmediate", "queueMicrotask"] });
    api.calendar.mockReset().mockResolvedValue({
      period: { from: "2026-10-01", to: "2026-10-31", timeZone: "UTC" },
      days: [
        dayOf({ date: "2026-10-16", ratings: { DAY: rating() }, entryCount: 1 }),
        dayOf({ date: "2026-10-17", entryCount: 2, pendingReviewCount: 2 }),
        dayOf({ date: "2026-10-15" }),
        dayOf({ date: "2026-10-14", ratings: { MORNING: null, DAY: null } as never }),
      ],
      dataRevision: 1,
    });
  });
  afterEach(() => jest.useRealTimers());

  it("показывает месяц и описывает дни для VoiceOver", async () => {
    await renderIn(<CalendarScreen />);

    expect(await screen.findByText("Октябрь 2026")).toBeTruthy();
    expect(await screen.findByLabelText("16 число, оценок: 1, записей: 1")).toBeTruthy();
    expect(screen.getByLabelText("17 число, записей: 2, ждут проверки: 2")).toBeTruthy();
    expect(screen.getByLabelText("15 число, нет данных")).toBeTruthy();
    expect(screen.getByLabelText("14 число, нет данных")).toBeTruthy();
    expect(screen.getByLabelText("20 число, нет данных")).toBeTruthy();
    expect(api.calendar).toHaveBeenCalledWith("2026-10-01", "2026-10-31", "UTC");
  });

  it("открывает выбранный день", async () => {
    await renderIn(<CalendarScreen />);

    await fireEvent.press(await screen.findByLabelText("16 число, оценок: 1, записей: 1"));

    expect(router.push).toHaveBeenCalledWith({ pathname: "/diary/day/[date]", params: { date: "2026-10-16" } });
  });

  it("переключает месяцы", async () => {
    await renderIn(<CalendarScreen />);
    await screen.findByText("Октябрь 2026");

    await fireEvent.press(screen.getByRole("button", { name: "Следующий месяц" }));
    expect(await screen.findByText("Ноябрь 2026")).toBeTruthy();
    expect(api.calendar).toHaveBeenLastCalledWith("2026-11-01", "2026-11-30", "UTC");

    await fireEvent.press(screen.getByRole("button", { name: "Предыдущий месяц" }));
    await fireEvent.press(screen.getByRole("button", { name: "Предыдущий месяц" }));
    expect(await screen.findByText("Сентябрь 2026")).toBeTruthy();
  });

  it("при ошибке позволяет повторить", async () => {
    api.calendar.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue({ period: {}, days: [], dataRevision: 1 } as never);
    await renderIn(<CalendarScreen />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByLabelText("1 число, нет данных")).toBeTruthy();
  });
});

describe("DayScreen", () => {
  const response = (overrides: Partial<DayResponse> = {}): DayResponse => ({
    day: dayOf({
      ratings: { MORNING: rating({ anxiety: 3, mood: 7, energy: 5 }), EVENING: rating({ anxiety: null, mood: null, energy: 2 }) },
      entryCount: 1,
      pendingReviewCount: 1,
      confirmedPresentTagCodes: ["SLEEP_DEFICIT", "ANXIETY"],
      ambiguousTagCodes: ["CALM"],
    }),
    entries: {
      items: [
        {
          id: "e1",
          occurredAt: "2026-10-18T09:30:00Z",
          timeZone: "UTC",
          localDateInRequestedZone: "2026-10-18",
          textPreview: "Плохо спал",
          revision: 1,
          version: 0,
          reportVisible: true,
          confirmedTags: [],
          analysisStatus: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    },
    occurrences: [
      {
        occurrenceId: "o1",
        medicationId: "m1",
        medicationName: "Препарат А",
        scheduleVersionId: "s1",
        scheduleSlotId: "sl1",
        plannedAt: "2026-10-18T08:00:00Z",
        plannedDose: "1",
        unit: "TABLET",
        timeZone: "UTC",
        status: "TAKEN",
        intake: null,
      },
    ],
    dataRevision: 1,
    ...overrides,
  });

  beforeEach(() => api.day.mockReset().mockResolvedValue(response()));

  it("показывает оценки по слотам, записи, теги и препараты", async () => {
    await renderIn(<DayScreen date="2026-10-18" />);

    expect(await screen.findByText("18 октября 2026 г.")).toBeTruthy();
    expect(screen.getByText("Утро: тревожность 3, настроение 7, энергия 5")).toBeTruthy();
    expect(screen.getByText("Вечер: тревожность —, настроение —, энергия 2")).toBeTruthy();
    expect(screen.getByText("Плохо спал")).toBeTruthy();
    expect(screen.getByText("Ждут проверки тегов: 1.")).toBeTruthy();
    expect(screen.getByText("Недостаток сна, Тревога")).toBeTruthy();
    expect(screen.getByText("Противоречия: Спокойствие.")).toBeTruthy();
    expect(screen.getByText("08:00 · Препарат А")).toBeTruthy();
    expect(screen.getByText("1 таб. · Принято")).toBeTruthy();
    expect(api.day).toHaveBeenCalledWith("2026-10-18", "UTC");
  });

  it("для пустого дня сообщает об отсутствии данных и скрывает лишние блоки", async () => {
    api.day.mockResolvedValue(response({ day: dayOf(), entries: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }, occurrences: [] }));
    await renderIn(<DayScreen date="2026-10-18" />);

    expect(await screen.findByText("Оценок за этот день нет.")).toBeTruthy();
    expect(screen.getByText("Записей за этот день нет.")).toBeTruthy();
    expect(screen.queryByText("Подтверждённые теги")).toBeNull();
    expect(screen.queryByText("Препараты")).toBeNull();
  });

  it("не показывает противоречия, когда их нет", async () => {
    api.day.mockResolvedValue(response({ day: dayOf({ confirmedPresentTagCodes: ["CALM"] }) }));
    await renderIn(<DayScreen date="2026-10-18" />);

    expect(await screen.findByText("Спокойствие")).toBeTruthy();
    expect(screen.queryByText(/Противоречия/)).toBeNull();
  });

  it("при ошибке позволяет повторить", async () => {
    api.day.mockRejectedValueOnce(new ApiError(0, "NETWORK_ERROR", "x")).mockResolvedValue(response());
    await renderIn(<DayScreen date="2026-10-18" />);

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Плохо спал")).toBeTruthy();
  });
});
