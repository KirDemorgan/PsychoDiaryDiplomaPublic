import { render, waitFor } from "@testing-library/react-native";

import { medicationsApi } from "@/api/medications-api";
import { settingsApi } from "@/api/settings-api";
import { queryWrapper } from "@/test/query-wrapper";

import { ReminderSync } from "./reminder-sync";
import { cancelAllReminders, replaceReminders } from "./scheduler";

jest.mock("@/lib/time-zone", () => ({ deviceTimeZone: () => "UTC" }));
jest.mock("@/api/medications-api", () => ({ medicationsApi: { occurrences: jest.fn() } }));
jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn() } }));
jest.mock("./scheduler", () => ({ replaceReminders: jest.fn(), cancelAllReminders: jest.fn() }));

const medications = medicationsApi as jest.Mocked<typeof medicationsApi>;
const settings = settingsApi as jest.Mocked<typeof settingsApi>;
const replace = replaceReminders as jest.Mock;
const cancel = cancelAllReminders as jest.Mock;

const future = new Date(Date.now() + 3_600_000).toISOString();
const item = { occurrenceId: "o1", medicationName: "П", plannedAt: future, plannedDose: "1", unit: "TABLET", status: "NO_DATA" };

async function renderSync() {
  const Wrapper = queryWrapper();
  await render(
    <Wrapper>
      <ReminderSync />
    </Wrapper>,
  );
}

describe("ReminderSync", () => {
  beforeEach(() => {
    replace.mockReset().mockResolvedValue(undefined);
    cancel.mockReset().mockResolvedValue(undefined);
    medications.occurrences.mockReset().mockResolvedValue({ items: [item] } as never);
  });

  it("планирует будущие приёмы, когда напоминания включены", async () => {
    settings.get.mockResolvedValue({ remindersEnabled: true, neutralNotificationText: true } as never);
    await renderSync();

    await waitFor(() => expect(replace).toHaveBeenCalled());
    expect(replace.mock.calls[0][0]).toEqual([
      expect.objectContaining({ id: "o1", title: "Напоминание" }),
    ]);
    expect(cancel).not.toHaveBeenCalled();
  });

  it("называет препарат, если нейтральный текст выключен", async () => {
    settings.get.mockResolvedValue({ remindersEnabled: true, neutralNotificationText: false } as never);
    await renderSync();

    await waitFor(() => expect(replace).toHaveBeenCalled());
    expect(replace.mock.calls[0][0][0]).toMatchObject({ title: "П", body: "Время приёма: 1 таб." });
  });

  it("отменяет все уведомления, когда напоминания выключены", async () => {
    settings.get.mockResolvedValue({ remindersEnabled: false, neutralNotificationText: true } as never);
    await renderSync();

    await waitFor(() => expect(cancel).toHaveBeenCalled());
    expect(replace).not.toHaveBeenCalled();
  });

  it("ничего не делает, пока настройки не загружены", async () => {
    settings.get.mockReturnValue(new Promise(() => undefined) as never);
    await renderSync();

    expect(replace).not.toHaveBeenCalled();
    expect(cancel).not.toHaveBeenCalled();
  });
});
