import type { OverviewMode, ReportStatus } from "@/api/reports-types";

export const STATUS_LABELS: Record<ReportStatus, string> = {
  QUEUED: "В очереди",
  RUNNING: "Готовится",
  READY: "Готов",
  FAILED: "Ошибка",
  CANCELLED: "Отменён",
};

export const OVERVIEW_MODE_LABELS: Record<OverviewMode, string> = {
  AI: "AI-обзор проверен программой",
  TEMPLATE: "Обзор по шаблону",
  NONE: "Без обзора",
};
