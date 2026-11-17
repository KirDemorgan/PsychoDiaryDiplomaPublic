import { QueryClientProvider } from "@tanstack/react-query";
import { DarkTheme, DefaultTheme, ThemeProvider } from "expo-router/react-navigation";
import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { useColorScheme } from "react-native";

import { useTheme } from "@/theme/use-theme";
import { sessionService } from "@/auth/session-service";
import { AppearanceSync } from "@/features/appearance/appearance-sync";
import { useOnboardingBootstrap } from "@/features/onboarding/use-onboarding-bootstrap";
import { cancelAllReminders } from "@/features/reminders/scheduler";
import { ReminderSync } from "@/features/reminders/reminder-sync";
import { useSessionStore } from "@/auth/session-store";
import { bindFocusToAppState, createQueryClient } from "@/lib/query-client";

SplashScreen.preventAutoHideAsync();

function navigationTheme(dark: boolean, background: string) {
  const base = dark ? DarkTheme : DefaultTheme;
  return { ...base, colors: { ...base.colors, background } };
}

export default function RootLayout() {
  const status = useSessionStore((state) => state.status);
  const onboardingStatus = useOnboardingBootstrap(status);
  const [queryClient] = useState(createQueryClient);
  const dark = useColorScheme() === "dark";
  const palette = useTheme();

  useEffect(() => {
    sessionService.restore();
    return bindFocusToAppState();
  }, []);

  const ready = status !== "restoring" && onboardingStatus !== "loading";

  useEffect(() => {
    if (ready) {
      SplashScreen.hideAsync();
    }
    if (status === "signedOut") {
      queryClient.clear();
      void cancelAllReminders();
    }
  }, [ready, status, queryClient]);

  if (!ready) {
    return null;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider value={navigationTheme(dark, palette.background)}>
        <StatusBar style="auto" />
        {status === "signedIn" ? (
          <>
            <ReminderSync />
            <AppearanceSync />
          </>
        ) : null}
        <Stack screenOptions={{ headerShown: false, headerBackButtonDisplayMode: "minimal" }}>
          <Stack.Screen name="index" />
          <Stack.Screen name="onboarding" options={{ animation: "fade", gestureEnabled: false }} />
          <Stack.Protected guard={status === "signedIn"}>
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="report/index" options={{ headerShown: true, title: "Отчёты" }} />
            <Stack.Screen
              name="report/new"
              options={{ headerShown: true, title: "Отчёт для врача", presentation: "modal" }}
            />
            <Stack.Screen name="report/[id]" options={{ headerShown: true, title: "Отчёт" }} />
            <Stack.Screen name="medications/index" options={{ headerShown: true, title: "Препараты" }} />
            <Stack.Screen
              name="medications/new"
              options={{ headerShown: true, title: "Новый препарат", presentation: "modal" }}
            />
            <Stack.Screen name="medications/[id]" options={{ headerShown: true, title: "Препарат" }} />
            <Stack.Screen
              name="entry/new"
              options={{ headerShown: true, title: "Новая запись", presentation: "modal" }}
            />
            <Stack.Screen name="entry/[id]" options={{ headerShown: true, title: "Запись" }} />
            <Stack.Screen name="entry/review/[id]" options={{ headerShown: true, title: "Проверка AI" }} />
          </Stack.Protected>
          <Stack.Protected guard={status === "signedOut"}>
            <Stack.Screen name="(auth)" />
          </Stack.Protected>
        </Stack>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
