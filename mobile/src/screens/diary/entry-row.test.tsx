import { render, screen } from "@testing-library/react-native";

import type { EntrySummary } from "@/api/entries-types";

import { EntryRow } from "./entry-row";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);

const entry: EntrySummary = {
  id: "e1",
  occurredAt: "2026-10-18T09:30:00Z",
  timeZone: "UTC",
  localDateInRequestedZone: null,
  textPreview: "Плохо спал, голова тяжёлая",
  revision: 1,
  version: 0,
  reportVisible: true,
  confirmedTags: [],
  analysisStatus: null,
};

describe("EntryRow", () => {
  it("показывает время и превью записи", async () => {
    await render(<EntryRow entry={entry} />);

    expect(screen.getByText("Плохо спал, голова тяжёлая")).toBeTruthy();
    expect(screen.getByText(/09:30/)).toBeTruthy();
  });

  it("показывает подтверждённые теги, если они есть", async () => {
    await render(<EntryRow entry={{ ...entry, confirmedTags: ["SLEEP_DEFICIT", "ANXIETY"] }} />);

    expect(screen.getByText("Недостаток сна · Тревога")).toBeTruthy();
  });

  it("не прячет неизвестный код тега, если сервер добавил новый", async () => {
    await render(<EntryRow entry={{ ...entry, confirmedTags: ["NEW_TAG"] }} />);

    expect(screen.getByText("NEW_TAG")).toBeTruthy();
  });
});
