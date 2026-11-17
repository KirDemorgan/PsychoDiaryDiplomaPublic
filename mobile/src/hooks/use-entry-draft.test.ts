import { act, renderHook, waitFor } from "@testing-library/react-native";

import { clearDraft, loadDraft, saveDraft } from "@/features/entries/draft-store";

import { useEntryDraft } from "./use-entry-draft";

jest.mock("@/features/entries/draft-store", () => ({ loadDraft: jest.fn(), saveDraft: jest.fn(), clearDraft: jest.fn() }));
let mockUuidCounter = 0;
jest.mock("expo-crypto", () => ({ randomUUID: () => `uuid-${++mockUuidCounter}` }));

const load = loadDraft as jest.Mock;
const save = saveDraft as jest.Mock;
const clear = clearDraft as jest.Mock;

describe("useEntryDraft", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockUuidCounter = 0;
    load.mockReset().mockResolvedValue(null);
    save.mockReset().mockResolvedValue(undefined);
    clear.mockReset().mockResolvedValue(undefined);
  });
  afterEach(() => jest.useRealTimers());

  it("без сохранённого черновика начинает с пустого текста", async () => {
    const { result } = await renderHook(() => useEntryDraft("u1"));

    await waitFor(() => expect(result.current.status).toBe("empty"));
    expect(result.current.text).toBe("");
  });

  it("восстанавливает текст и ключ идемпотентности из черновика", async () => {
    load.mockResolvedValue({ text: "вчера", idempotencyKey: "saved-key", updatedAt: "t" });
    const { result } = await renderHook(() => useEntryDraft("u1"));

    await waitFor(() => expect(result.current.status).toBe("savedLocally"));
    expect(result.current.text).toBe("вчера");
    expect(result.current.getIdempotencyKey()).toBe("saved-key");
  });

  it("сохраняет черновик с задержкой, склеивая быстрые правки", async () => {
    const { result } = await renderHook(() => useEntryDraft("u1"));
    await waitFor(() => expect(result.current.status).toBe("empty"));

    await act(async () => {
      result.current.setText("при");
      result.current.setText("привет");
      jest.advanceTimersByTime(400);
    });

    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith("u1", expect.objectContaining({ text: "привет", idempotencyKey: "uuid-1" }));
    expect(result.current.status).toBe("savedLocally");
  });

  it("удаляет черновик, когда текст стёрт", async () => {
    const { result } = await renderHook(() => useEntryDraft("u1"));
    await waitFor(() => expect(result.current.status).toBe("empty"));

    await act(async () => {
      result.current.setText("   ");
      jest.advanceTimersByTime(400);
    });

    expect(clear).toHaveBeenCalledWith("u1");
    expect(result.current.status).toBe("empty");
  });

  it("сообщает, если сохранить на устройстве не удалось", async () => {
    save.mockRejectedValue(new Error("disk"));
    const { result } = await renderHook(() => useEntryDraft("u1"));
    await waitFor(() => expect(result.current.status).toBe("empty"));

    await act(async () => {
      result.current.setText("текст");
      jest.advanceTimersByTime(400);
    });

    expect(result.current.status).toBe("saveFailed");
  });

  it("сбрасывает черновик и выдаёт новый ключ", async () => {
    const { result } = await renderHook(() => useEntryDraft("u1"));
    await waitFor(() => expect(result.current.status).toBe("empty"));
    await act(async () => {
      result.current.setText("текст");
    });

    await act(async () => {
      await result.current.discard();
    });

    expect(result.current.text).toBe("");
    expect(clear).toHaveBeenCalledWith("u1");
    expect(result.current.getIdempotencyKey()).toBe("uuid-2");
    expect(save).not.toHaveBeenCalled();
  });

  it("при уходе с экрана не теряет несохранённую правку", async () => {
    const { result, unmount } = await renderHook(() => useEntryDraft("u1"));
    await waitFor(() => expect(result.current.status).toBe("empty"));
    await act(async () => {
      result.current.setText("не успел");
    });

    await unmount();

    expect(save).toHaveBeenCalledWith("u1", expect.objectContaining({ text: "не успел" }));
  });

  it("не меняет состояние после размонтирования до загрузки", async () => {
    let resolveLoad: (value: null) => void = () => undefined;
    load.mockReturnValue(new Promise((resolve) => (resolveLoad = resolve)));
    const { unmount } = await renderHook(() => useEntryDraft("u1"));

    await unmount();
    await act(async () => resolveLoad(null));

    expect(save).not.toHaveBeenCalled();
  });
});
