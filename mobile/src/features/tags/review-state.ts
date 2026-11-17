import type { Candidate, ManualTag, TagCode, TagDecision, TagPresence, TagReviewRequest, TagTemporal } from "@/api/tags-types";

export type CandidateDecision =
  | { action: "ACCEPT" }
  | { action: "REJECT" }
  | { action: "EDIT"; replacement: ManualTag };

export type DecisionMap = Record<string, CandidateDecision>;

export interface CandidateEdit {
  labelCode: TagCode;
  presence: TagPresence;
  temporal: TagTemporal;
}

export function initialDecisions(candidates: readonly Candidate[]): DecisionMap {
  return Object.fromEntries(candidates.map((candidate) => [candidate.candidateId, { action: "ACCEPT" } as const]));
}

export function toggleDecision(decisions: DecisionMap, candidateId: string): DecisionMap {
  const accepted = decisions[candidateId]?.action !== "REJECT";
  return { ...decisions, [candidateId]: accepted ? { action: "REJECT" } : { action: "ACCEPT" } };
}

export function applyEdit(
  decisions: DecisionMap,
  candidate: Candidate,
  edit: CandidateEdit,
  appliesToLocalDate: string,
): DecisionMap {
  const unchanged =
    edit.labelCode === candidate.labelCode &&
    edit.presence === candidate.presence &&
    edit.temporal === candidate.temporal;
  if (unchanged) {
    return { ...decisions, [candidate.candidateId]: { action: "ACCEPT" } };
  }
  const replacement: ManualTag = {
    labelCode: edit.labelCode,
    quote: candidate.quote,
    start: candidate.start,
    end: candidate.end,
    presence: edit.presence,
    temporal: edit.temporal,
    subject: candidate.subject,
    appliesToLocalDate,
  };
  return { ...decisions, [candidate.candidateId]: { action: "EDIT", replacement } };
}

export function countAccepted(decisions: DecisionMap): number {
  return Object.values(decisions).filter((decision) => decision.action !== "REJECT").length;
}

export function buildReviewRequest(
  revision: number,
  candidates: readonly Candidate[],
  decisions: DecisionMap,
  appliesToLocalDate: string,
): TagReviewRequest {
  const list: TagDecision[] = candidates.map((candidate) => {
    const decision = decisions[candidate.candidateId] ?? { action: "ACCEPT" };
    switch (decision.action) {
      case "REJECT":
        return { candidateId: candidate.candidateId, action: "REJECT" };
      case "EDIT":
        return {
          candidateId: candidate.candidateId,
          action: "EDIT",
          replacement: decision.replacement,
          appliesToLocalDate,
        };
      default:
        return { candidateId: candidate.candidateId, action: "ACCEPT", appliesToLocalDate };
    }
  });
  return { revision, complete: true, decisions: list, manualTags: [] };
}
