import { focusManager } from "@tanstack/react-query";
import { AppState } from "react-native";

import { ApiError, NETWORK_ERROR } from "@/api/errors";

import { bindFocusToAppState, createQueryClient } from "./query-client";

describe("createQueryClient", () => {
  const retry = createQueryClient().getDefaultOptions().queries?.retry as (count: number, error: unknown) => boolean;

  it("повторяет при сбоях связи и ошибках сервера", () => {
    expect(retry(0, new ApiError(0, NETWORK_ERROR, "x"))).toBe(true);
    expect(retry(1, new ApiError(503, "X", "x"))).toBe(true);
  });

  it("не повторяет клиентские ошибки", () => {
    expect(retry(0, new ApiError(404, "X", "x"))).toBe(false);
  });

  it("ограничивает число повторов", () => {
    expect(retry(2, new ApiError(503, "X", "x"))).toBe(false);
  });

  it("повторяет неизвестные ошибки в пределах лимита", () => {
    expect(retry(0, new Error("boom"))).toBe(true);
  });
});

describe("bindFocusToAppState", () => {
  it("передаёт React Query активность приложения и отписывается", () => {
    const remove = jest.fn();
    let listener: (state: string) => void = () => undefined;
    const add = jest.spyOn(AppState, "addEventListener").mockImplementation(((_event: string, handler: (state: string) => void) => {
      listener = handler;
      return { remove };
    }) as never);
    const setFocused = jest.spyOn(focusManager, "setFocused");

    const unbind = bindFocusToAppState();
    listener("active");
    listener("background");
    unbind();

    expect(setFocused).toHaveBeenNthCalledWith(1, true);
    expect(setFocused).toHaveBeenNthCalledWith(2, false);
    expect(remove).toHaveBeenCalled();
    add.mockRestore();
    setFocused.mockRestore();
  });
});
