import { buildReportRequest, DEFAULT_OPTIONS, MAX_COMPARISON_TAGS } from "./build-request";
import { OVERVIEW_MODE_LABELS, STATUS_LABELS } from "./labels";

const now = new Date("2026-10-18T12:00:00Z");
const base = { days: 30, timeZone: "UTC", displayName: "", options: DEFAULT_OPTIONS, comparisonTags: [], now } as const;

describe("buildReportRequest", () => {
  it("задаёт период, зону и разделы по умолчанию без AI-обзора", () => {
    const request = buildReportRequest({ ...base });

    expect(request).toEqual({
      from: "2026-09-19",
      to: "2026-10-18",
      timeZone: "UTC",
      sections: ["RATINGS", "TAGS", "MEDICATIONS", "NOTES"],
      entrySelection: "ALL_VISIBLE",
      medicationSelection: "ALL",
      comparisonTagCodes: [],
    });
  });

  it("добавляет псевдоним без лишних пробелов и не включает пустой", () => {
    expect(buildReportRequest({ ...base, displayName: "  Анна " }).displayName).toBe("Анна");
    expect("displayName" in buildReportRequest({ ...base, displayName: "   " })).toBe(false);
  });

  it("отключённые записи и препараты исключаются из выборки", () => {
    const request = buildReportRequest({
      ...base,
      options: { ratings: true, tags: false, medications: false, notes: false, aiOverview: true },
    });

    expect(request.sections).toEqual(["RATINGS", "AI_OVERVIEW"]);
    expect(request.entrySelection).toBe("NONE");
    expect(request.medicationSelection).toBe("NONE");
  });

  it("передаёт теги для сравнения только при включённых тегах и не больше лимита", () => {
    const tags = ["ANXIETY", "CALM", "FATIGUE", "NAUSEA", "HEADACHE", "TREMOR", "CAFFEINE", "CONFLICT", "WORK_STUDY",
      "SOCIAL_EVENT", "PHYSICAL_ACTIVITY", "SLEEP_DEFICIT", "ANXIETY"] as const;

    expect(buildReportRequest({ ...base, comparisonTags: tags }).comparisonTagCodes).toHaveLength(MAX_COMPARISON_TAGS);
    expect(
      buildReportRequest({ ...base, comparisonTags: ["CALM"], options: { ...DEFAULT_OPTIONS, tags: false } })
        .comparisonTagCodes,
    ).toEqual([]);
  });

  it("описывает статусы и режим обзора", () => {
    expect(STATUS_LABELS.READY).toBe("Готов");
    expect(OVERVIEW_MODE_LABELS.TEMPLATE).toBe("Обзор по шаблону");
  });
});
