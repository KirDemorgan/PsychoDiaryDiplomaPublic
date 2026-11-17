export type TagCode =
  | "SLEEP_DEFICIT"
  | "WORK_STUDY"
  | "SOCIAL_EVENT"
  | "CONFLICT"
  | "PHYSICAL_ACTIVITY"
  | "CAFFEINE"
  | "ANXIETY"
  | "CALM"
  | "FATIGUE"
  | "NAUSEA"
  | "HEADACHE"
  | "TREMOR";

export type TagPresence = "PRESENT" | "NEGATED" | "UNCERTAIN";
export type TagTemporal = "CURRENT" | "PAST" | "FUTURE" | "UNSPECIFIED";
export type TagSubject = "SELF" | "OTHER" | "UNSPECIFIED";
export type TagAction = "ACCEPT" | "EDIT" | "REJECT" | "PENDING";
export type TagCategory = "CONTEXT" | "STATE" | "SYMPTOM";

export type AnalysisStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "STALE";

export interface TagDefinition {
  code: TagCode;
  labelRu: string;
  category: TagCategory;
}

export interface TagDefinitions {
  version: string;
  items: TagDefinition[];
}

export interface Candidate {
  candidateId: string;
  labelCode: TagCode;
  category: TagCategory;
  quote: string;
  start: number;
  end: number;
  presence: TagPresence;
  temporal: TagTemporal;
  subject: TagSubject;
}

export interface AnalysisResponse {
  id: string;
  entryId: string;
  revision: number;
  status: AnalysisStatus;
  attempt: number;
  createdAt: string;
  finishedAt: string | null;
  errorCode: string | null;
  result: { candidates: Candidate[] } | null;
  modelId: string | null;
  promptVersion: string | null;
  schemaVersion: string | null;
}

export interface ManualTag {
  labelCode: TagCode;
  quote: string;
  start: number;
  end: number;
  presence: TagPresence;
  temporal: TagTemporal;
  subject: TagSubject;
  appliesToLocalDate: string | null;
}

export interface TagDecision {
  candidateId: string;
  action: TagAction;
  replacement?: ManualTag;
  appliesToLocalDate?: string;
}

export interface TagReviewRequest {
  revision: number;
  complete: boolean;
  decisions: TagDecision[];
  manualTags: ManualTag[];
}
