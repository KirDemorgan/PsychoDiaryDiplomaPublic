import { fireEvent, render, screen } from "@testing-library/react-native";

import { analyticsApi } from "@/api/analytics-api";
import type { Comparison, Overview } from "@/api/analytics-types";
import { ApiError } from "@/api/errors";
import { queryWrapper } from "@/test/query-wrapper";

import { ComparisonScreen } from "./comparison-screen";
import { DynamicsScreen } from ".";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);
jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/analytics-api", () => ({ analyticsApi: { overview: jest.fn(), comparison: jest.fn() } }));

const api = analyticsApi as jest.Mocked<typeof analyticsApi>;

const overview = (overrides: Partial<Overview> = {}): Overview => ({
  period: { from: "2026-10-12", to: "2026-10-18", timeZone: "UTC" },
  dataRevision: 1,
  coverage: {
    metrics: {
      ANXIETY: { filled: 5, expected: 21, filledDays: 5, totalDays: 7 },
      MOOD: { filled: 5, expected: 21, filledDays: 5, totalDays: 7 },
      ENERGY: { filled: 5, expected: 21, filledDays: 5, totalDays: 7 },
    },
    filledSlots: 15,
    expectedSlots: 21,
    filledDays: 6,
    totalDays: 7,
  },
  dailyValues: [
    {
      date: "2026-10-16",
      anxietyMean: "8.0",
      anxietySlotCount: 2,
      moodMean: "5.0",
      moodSlotCount: 2,
      energyMean: "4.0",
      energySlotCount: 2,
    },
  ],
  tagFrequencies: [{ code: "SLEEP_DEFICIT", occurrences: 4, days: 3 }],
  medicationAdherence: [
    {
      medicationId: "m1",
      medicationName: "Препарат А",
      planned: 10,
      taken: 6,
      skipped: 1,
      noData: 3,
      takenRate: "0.6",
      markedCoverage: "0.7",
      unplanned: 0,
    },
  ],
  highAnxietyDays: [{ date: "2026-10-16", mean: "8.0", slotCount: 2, threshold: 7, rule: "UI" }],
  ambiguousContext: [],
  limitations: ["Сон берётся только из дневника."],
  ...overrides,
});

const comparison = (overrides: Partial<Comparison> = {}): Comparison => ({
  tagCode: "SLEEP_DEFICIT",
  metric: "ANXIETY",
  period: { from: "2026-10-12", to: "2026-10-18", timeZone: "UTC" },
  status: "SUFFICIENT",
  withMention: { nDays: 6, mean: "6.2", dates: [] },
  withoutMention: { nDays: 8, mean: "4.1", dates: [] },
  excluded: { noRating: 1, noEntry: 2, incompleteContext: 0, ambiguousContext: 0 },
  delta: "2.1",
  limitations: ["Связь не доказывает причину."],
  dataRevision: 1,
  ...overrides,
});

async function renderIn(ui: React.ReactElement) {
  const Wrapper = queryWrapper();
  await render(<Wrapper>{ui}</Wrapper>);
}

describe("DynamicsScreen", () => {
  beforeEach(() => {
    api.overview.mockReset().mockResolvedValue(overview());
  });

  it("показывает график, заполненность, теги, препараты и дни высокой тревожности", async () => {
    await renderIn(<DynamicsScreen />);

    expect(await screen.findByText("Заполнено 6 из 7 дней")).toBeTruthy();
    expect(screen.getByRole("image", { name: "Тревожность за 7 дн." })).toBeTruthy();
    expect(screen.getByText("Недостаток сна")).toBeTruthy();
    expect(screen.getByText("3 дн.")).toBeTruthy();
    expect(screen.getByText(/Принято 6 из 10 плановых \(60%\)\. Отмечено 70% доз; без отметки 3\./)).toBeTruthy();
    expect(screen.getByText("16.10 · среднее 8,0 из 10")).toBeTruthy();
    expect(screen.getByText("Сон берётся только из дневника.")).toBeTruthy();
  });

  it("не показывает теги, которых не было в периоде", async () => {
    api.overview.mockResolvedValue(
      overview({
        tagFrequencies: [
          { code: "SLEEP_DEFICIT", occurrences: 4, days: 3 },
          { code: "CAFFEINE", occurrences: 0, days: 0 },
        ],
      }),
    );
    await renderIn(<DynamicsScreen />);

    expect(await screen.findByText("Недостаток сна")).toBeTruthy();
    expect(screen.queryByText("Кофеин")).toBeNull();
  });

  it("запрашивает данные за выбранный период", async () => {
    await renderIn(<DynamicsScreen />);
    await screen.findByText("Заполнено 6 из 7 дней");

    await fireEvent.press(screen.getByRole("radio", { name: "30 дней" }));

    expect(await screen.findByRole("image", { name: "Тревожность за 30 дн." })).toBeTruthy();
    const [from, to] = api.overview.mock.calls.at(-1)!;
    expect((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000).toBe(29);
  });

  it("переключает показатель", async () => {
    await renderIn(<DynamicsScreen />);
    await screen.findByText("Заполнено 6 из 7 дней");

    await fireEvent.press(screen.getByRole("radio", { name: "Настроение" }));

    expect(screen.getByRole("image", { name: "Настроение за 7 дн." })).toBeTruthy();
  });

  it("сообщает, что оценок за период нет, и что тегов нет", async () => {
    api.overview.mockResolvedValue(
      overview({ dailyValues: [], tagFrequencies: [], medicationAdherence: [], highAnxietyDays: [], limitations: [] }),
    );
    await renderIn(<DynamicsScreen />);

    expect(await screen.findByText("За выбранный период оценок нет.")).toBeTruthy();
    expect(screen.getByText("Подтверждённых тегов за период нет.")).toBeTruthy();
    expect(screen.queryByText("Приём препаратов")).toBeNull();
    expect(screen.queryByText("Дни с высокой тревожностью")).toBeNull();
  });

  it("при ошибке показывает сообщение и позволяет повторить", async () => {
    api.overview.mockRejectedValueOnce(new ApiError(503, "X", "x")).mockResolvedValue(overview());
    await renderIn(<DynamicsScreen />);

    expect(await screen.findByText("Сервер временно недоступен. Попробуйте позже.")).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText("Заполнено 6 из 7 дней")).toBeTruthy();
  });
});

describe("ComparisonScreen", () => {
  const props = { code: "SLEEP_DEFICIT", metric: "ANXIETY", from: "2026-10-12", to: "2026-10-18" } as const;

  beforeEach(() => {
    api.comparison.mockReset().mockResolvedValue(comparison());
  });

  it("описывает достаточное сравнение с оговоркой о причине и исключёнными днями", async () => {
    await renderIn(<ComparisonScreen {...props} />);

    expect(await screen.findByText(/среднем 6,2 из 10 \(6 дн\.\), в дни без упоминания 4,1 \(8 дн\.\)\. Разница 2,1\./)).toBeTruthy();
    expect(screen.getByText("Это связь, а не доказанная причина.")).toBeTruthy();
    expect(screen.getByText("Без оценки: 1 дн.")).toBeTruthy();
    expect(screen.getByText("Без записей: 2 дн.")).toBeTruthy();
    expect(screen.getByText("Связь не доказывает причину.")).toBeTruthy();
    expect(api.comparison).toHaveBeenCalledWith("2026-10-12", "2026-10-18", "UTC", "SLEEP_DEFICIT", "ANXIETY");
  });

  it("при нехватке данных не показывает числа сравнения", async () => {
    api.comparison.mockResolvedValue(
      comparison({ status: "INSUFFICIENT_DATA", delta: null, withMention: { nDays: 2, mean: "6", dates: [] } }),
    );
    await renderIn(<ComparisonScreen {...props} />);

    expect(await screen.findByText(/Недостаточно данных для сравнения: нужно не менее 5 дней в каждой группе/)).toBeTruthy();
    expect(screen.queryByText(/Разница/)).toBeNull();
  });

  it("показывает все причины исключения дней", async () => {
    api.comparison.mockResolvedValue(
      comparison({ excluded: { noRating: 0, noEntry: 0, incompleteContext: 3, ambiguousContext: 1 }, limitations: [] }),
    );
    await renderIn(<ComparisonScreen {...props} />);

    expect(await screen.findByText("Записи не проверены полностью: 3 дн.")).toBeTruthy();
    expect(screen.getByText("Противоречивый контекст: 1 дн.")).toBeTruthy();
  });

  it("скрывает блок исключений, если ничего не исключено", async () => {
    api.comparison.mockResolvedValue(
      comparison({ excluded: { noRating: 0, noEntry: 0, incompleteContext: 0, ambiguousContext: 0 }, limitations: [] }),
    );
    await renderIn(<ComparisonScreen {...props} />);

    await screen.findByText(/Это связь/);
    expect(screen.queryByText("Не вошло в расчёт")).toBeNull();
  });

  it("при ошибке позволяет повторить", async () => {
    api.comparison.mockRejectedValueOnce(new ApiError(0, "NETWORK_ERROR", "x")).mockResolvedValue(comparison());
    await renderIn(<ComparisonScreen {...props} />);

    expect(await screen.findByText(/Нет связи с сервером/)).toBeTruthy();
    await fireEvent.press(screen.getByRole("button", { name: "Повторить" }));

    expect(await screen.findByText(/Это связь/)).toBeTruthy();
  });
});
