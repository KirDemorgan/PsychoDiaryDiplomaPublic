export type OnboardingPageId = "ratings" | "tags" | "medications" | "report";

export interface OnboardingPageContent {
  id: OnboardingPageId;
  title: string;
  body: string;
}

export const ONBOARDING_PAGES: readonly OnboardingPageContent[] = [
  {
    id: "ratings",
    title: "Следите за состоянием",
    body: "Три оценки в день: тревожность, настроение и энергия. Утром, днём и вечером — столько, сколько успеете.",
  },
  {
    id: "tags",
    title: "Теги предлагает AI, решаете вы",
    body: "Опишите день своими словами. Модель предложит теги, а вы подтвердите или поправите. AI включается отдельно и выключается в любой момент.",
  },
  {
    id: "medications",
    title: "Препараты под рукой",
    body: "Задайте схему приёма, отмечайте «принял» или «пропустил» и получайте напоминания.",
  },
  {
    id: "report",
    title: "Отчёт для врача",
    body: "Соберите PDF за период и покажите на приёме. Это материал для разговора, а не медицинское заключение.",
  },
];
