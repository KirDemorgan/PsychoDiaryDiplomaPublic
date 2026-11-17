import { Appearance } from "react-native";

export type ThemePreference = "SYSTEM" | "LIGHT" | "DARK";

export const THEME_OPTIONS = [
  { value: "SYSTEM", label: "Системная" },
  { value: "LIGHT", label: "Светлая" },
  { value: "DARK", label: "Тёмная" },
] as const;

export function applyThemePreference(preference: string | null | undefined): void {
  Appearance.setColorScheme(preference === "LIGHT" ? "light" : preference === "DARK" ? "dark" : "unspecified");
}
