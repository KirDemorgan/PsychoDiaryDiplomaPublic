import AsyncStorage from "@react-native-async-storage/async-storage";

import { clearDraft, loadDraft, saveDraft } from "./draft-store";

jest.mock("@react-native-async-storage/async-storage", () =>
  jest.requireActual("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

describe("draft store", () => {
  beforeEach(() => AsyncStorage.clear());

  it("сохраняет и читает черновик отдельно для каждого пользователя", async () => {
    await saveDraft("u1", { text: "один", idempotencyKey: "k1", updatedAt: "t1" });
    await saveDraft("u2", { text: "два", idempotencyKey: "k2", updatedAt: "t2" });

    await expect(loadDraft("u1")).resolves.toEqual({ text: "один", idempotencyKey: "k1", updatedAt: "t1" });
    await expect(loadDraft("u2")).resolves.toMatchObject({ text: "два" });
  });

  it("возвращает null, если черновика нет", async () => {
    await expect(loadDraft("nobody")).resolves.toBeNull();
  });

  it("удаляет черновик", async () => {
    await saveDraft("u1", { text: "один", idempotencyKey: "k1", updatedAt: "t1" });

    await clearDraft("u1");

    await expect(loadDraft("u1")).resolves.toBeNull();
  });

  it.each([
    ["повреждённый JSON", "{not json"],
    ["без текста", JSON.stringify({ idempotencyKey: "k" })],
    ["без ключа", JSON.stringify({ text: "т" })],
  ])("игнорирует %s", async (_name, raw) => {
    await AsyncStorage.setItem("diary.entry-draft.u1", raw);

    await expect(loadDraft("u1")).resolves.toBeNull();
  });

  it("подставляет пустую метку времени, если её нет", async () => {
    await AsyncStorage.setItem("diary.entry-draft.u1", JSON.stringify({ text: "т", idempotencyKey: "k" }));

    await expect(loadDraft("u1")).resolves.toEqual({ text: "т", idempotencyKey: "k", updatedAt: "" });
  });
});
