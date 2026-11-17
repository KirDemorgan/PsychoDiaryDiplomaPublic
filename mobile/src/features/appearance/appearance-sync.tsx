import { useEffect } from "react";

import { useSettings } from "@/hooks/use-settings";

import { applyThemePreference } from "./theme-preference";

export function AppearanceSync() {
  const theme = useSettings().data?.theme;

  useEffect(() => {
    if (theme !== undefined) {
      applyThemePreference(theme);
    }
  }, [theme]);

  return null;
}
