import { act, renderHook, waitFor } from "@testing-library/react-native";
import { AccessibilityInfo } from "react-native";

import { useReduceMotion } from "./use-reduce-motion";

describe("useReduceMotion", () => {
  it("читает системную настройку и следит за её изменением", async () => {
    jest.spyOn(AccessibilityInfo, "isReduceMotionEnabled").mockResolvedValue(true);
    let listener: (value: boolean) => void = () => undefined;
    const remove = jest.fn();
    jest.spyOn(AccessibilityInfo, "addEventListener").mockImplementation(((_event: string, handler: (value: boolean) => void) => {
      listener = handler;
      return { remove };
    }) as never);

    const { result, unmount } = await renderHook(() => useReduceMotion());

    await waitFor(() => expect(result.current).toBe(true));
    await act(async () => listener(false));
    expect(result.current).toBe(false);

    await unmount();
    expect(remove).toHaveBeenCalled();
  });

  it("не обновляет состояние после размонтирования", async () => {
    let resolve: (value: boolean) => void = () => undefined;
    jest.spyOn(AccessibilityInfo, "isReduceMotionEnabled").mockReturnValue(new Promise((r) => (resolve = r)));
    jest.spyOn(AccessibilityInfo, "addEventListener").mockReturnValue({ remove: jest.fn() } as never);
    const { result, unmount } = await renderHook(() => useReduceMotion());

    await unmount();
    await act(async () => resolve(true));

    expect(result.current).toBe(false);
  });
});
