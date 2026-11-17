import type { Candidate } from "@/api/tags-types";

import {
  applyEdit,
  buildReviewRequest,
  countAccepted,
  initialDecisions,
  toggleDecision,
} from "./review-state";

const candidate = (id: string, overrides: Partial<Candidate> = {}): Candidate => ({
  candidateId: id,
  labelCode: "ANXIETY",
  category: "STATE",
  quote: "тревожился",
  start: 10,
  end: 20,
  presence: "PRESENT",
  temporal: "CURRENT",
  subject: "SELF",
  ...overrides,
});

describe("review state", () => {
  const candidates = [candidate("a"), candidate("b", { labelCode: "CALM" })];

  it("по умолчанию принимает все кандидаты", () => {
    expect(initialDecisions(candidates)).toEqual({ a: { action: "ACCEPT" }, b: { action: "ACCEPT" } });
  });

  it("переключает принят/отклонён и возвращает обратно", () => {
    const rejected = toggleDecision(initialDecisions(candidates), "a");
    expect(rejected.a).toEqual({ action: "REJECT" });
    expect(toggleDecision(rejected, "a").a).toEqual({ action: "ACCEPT" });
  });

  it("исправленный тег при переключении отклоняется", () => {
    const edited = applyEdit(initialDecisions(candidates), candidates[0], {
      labelCode: "FATIGUE",
      presence: "PRESENT",
      temporal: "CURRENT",
    }, "2026-10-18");

    expect(toggleDecision(edited, "a").a).toEqual({ action: "REJECT" });
  });

  it("считает принятые, включая исправленные", () => {
    const edited = applyEdit(initialDecisions(candidates), candidates[0], {
      labelCode: "FATIGUE",
      presence: "PRESENT",
      temporal: "CURRENT",
    }, "2026-10-18");

    expect(countAccepted(edited)).toBe(2);
    expect(countAccepted(toggleDecision(edited, "b"))).toBe(1);
  });

  it("исправление без изменений остаётся принятием", () => {
    const result = applyEdit(
      initialDecisions(candidates),
      candidates[0],
      { labelCode: "ANXIETY", presence: "PRESENT", temporal: "CURRENT" },
      "2026-10-18",
    );

    expect(result.a).toEqual({ action: "ACCEPT" });
  });

  it("исправление сохраняет цитату и позиции кандидата", () => {
    const result = applyEdit(
      initialDecisions(candidates),
      candidates[0],
      { labelCode: "CALM", presence: "NEGATED", temporal: "PAST" },
      "2026-10-18",
    );

    expect(result.a).toEqual({
      action: "EDIT",
      replacement: {
        labelCode: "CALM",
        quote: "тревожился",
        start: 10,
        end: 20,
        presence: "NEGATED",
        temporal: "PAST",
        subject: "SELF",
        appliesToLocalDate: "2026-10-18",
      },
    });
  });

  it("собирает полный запрос подтверждения с датой для принятых и исправленных", () => {
    let decisions = initialDecisions([...candidates, candidate("c")]);
    decisions = toggleDecision(decisions, "b");
    decisions = applyEdit(decisions, candidates[0], { labelCode: "CALM", presence: "PRESENT", temporal: "CURRENT" }, "2026-10-18");

    const request = buildReviewRequest(3, [...candidates, candidate("c")], decisions, "2026-10-18");

    expect(request).toMatchObject({ revision: 3, complete: true, manualTags: [] });
    expect(request.decisions).toEqual([
      expect.objectContaining({ candidateId: "a", action: "EDIT", appliesToLocalDate: "2026-10-18" }),
      { candidateId: "b", action: "REJECT" },
      { candidateId: "c", action: "ACCEPT", appliesToLocalDate: "2026-10-18" },
    ]);
  });

  it("считает кандидата без решения принятым", () => {
    const request = buildReviewRequest(1, [candidate("z")], {}, "2026-10-18");

    expect(request.decisions).toEqual([{ candidateId: "z", action: "ACCEPT", appliesToLocalDate: "2026-10-18" }]);
  });

  it("допускает завершение без кандидатов", () => {
    expect(buildReviewRequest(1, [], {}, "2026-10-18")).toEqual({
      revision: 1,
      complete: true,
      decisions: [],
      manualTags: [],
    });
  });
});
