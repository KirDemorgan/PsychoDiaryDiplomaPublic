import { act, renderHook } from "@testing-library/react-native";

import { useDebouncedValue } from "./use-debounced-value";

describe("useDebouncedValue", () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it("отдаёт новое значение только после паузы", async () => {
    const { result, rerender } = await renderHook(({ value }: { value: string }) => useDebouncedValue(value, 300), {
      initialProps: { value: "a" },
    });

    await rerender({ value: "ab" });
    expect(result.current).toBe("a");

    await act(async () => {
      jest.advanceTimersByTime(299);
    });
    expect(result.current).toBe("a");

    await act(async () => {
      jest.advanceTimersByTime(1);
    });
    expect(result.current).toBe("ab");
  });
});
