import { render, waitFor } from "@testing-library/react-native";
import { Appearance } from "react-native";

import { settingsApi } from "@/api/settings-api";
import { queryWrapper } from "@/test/query-wrapper";

import { AppearanceSync } from "./appearance-sync";
import { applyThemePreference, THEME_OPTIONS } from "./theme-preference";

jest.mock("@/api/settings-api", () => ({ settingsApi: { get: jest.fn() } }));

const api = settingsApi as jest.Mocked<typeof settingsApi>;

describe("theme preference", () => {
  const spy = jest.spyOn(Appearance, "setColorScheme").mockImplementation(() => undefined);

  beforeEach(() => spy.mockClear());

  it.each([
    ["LIGHT", "light"],
    ["DARK", "dark"],
    ["SYSTEM", "unspecified"],
    [null, "unspecified"],
    ["что-то другое", "unspecified"],
  ])("настройка %p задаёт схему %p", (preference, expected) => {
    applyThemePreference(preference);

    expect(spy).toHaveBeenCalledWith(expected);
  });

  it("предлагает три варианта", () => {
    expect(THEME_OPTIONS.map((option) => option.value)).toEqual(["SYSTEM", "LIGHT", "DARK"]);
  });

  it("AppearanceSync применяет тему из настроек", async () => {
    api.get.mockResolvedValue({ theme: "DARK", version: 1 } as never);
    const Wrapper = queryWrapper();

    await render(
      <Wrapper>
        <AppearanceSync />
      </Wrapper>,
    );

    await waitFor(() => expect(spy).toHaveBeenCalledWith("dark"));
  });

  it("AppearanceSync ничего не делает, пока настройки не загружены", async () => {
    api.get.mockReturnValue(new Promise(() => undefined) as never);
    const Wrapper = queryWrapper();

    await render(
      <Wrapper>
        <AppearanceSync />
      </Wrapper>,
    );

    expect(spy).not.toHaveBeenCalled();
  });
});
