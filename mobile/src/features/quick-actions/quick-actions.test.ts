import { renderHook } from "@testing-library/react-native";

import { router } from "@/test/mocks";

import { useQuickActionStore } from "./quick-action-store";
import { usePendingQuickAction } from "./use-pending-quick-action";

jest.mock("expo-router", () => jest.requireActual("@/test/mocks").routerMock);

describe("quick action store", () => {
  beforeEach(() => useQuickActionStore.setState({ pending: null }));

  it("запоминает действие и отдаёт его один раз", () => {
    useQuickActionStore.getState().request("new-entry");

    expect(useQuickActionStore.getState().consume()).toBe("new-entry");
    expect(useQuickActionStore.getState().consume()).toBeNull();
  });
});

describe("usePendingQuickAction", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useQuickActionStore.setState({ pending: null });
  });

  it("после входа открывает новую запись и сбрасывает действие", async () => {
    useQuickActionStore.getState().request("new-entry");

    await renderHook(() => usePendingQuickAction("signedIn"));

    expect(router.push).toHaveBeenCalledWith("/entry/new");
    expect(router.push).toHaveBeenCalledTimes(1);
    expect(useQuickActionStore.getState().pending).toBeNull();
  });

  it.each(["signedOut", "restoring"] as const)("при статусе %s держит действие до входа", async (status) => {
    useQuickActionStore.getState().request("new-entry");

    await renderHook(() => usePendingQuickAction(status));

    expect(router.push).not.toHaveBeenCalled();
    expect(useQuickActionStore.getState().pending).toBe("new-entry");
  });

  it("без отложенного действия ничего не открывает", async () => {
    await renderHook(() => usePendingQuickAction("signedIn"));

    expect(router.push).not.toHaveBeenCalled();
  });
});
