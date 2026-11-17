import { Stack } from "expo-router/stack";

import { useTheme } from "@/theme/use-theme";

export function TabStack({ title }: { title: string }) {
  const theme = useTheme();
  return (
    <Stack
      screenOptions={({ route }) => ({
        contentStyle: { backgroundColor: theme.background },
        headerTransparent: true,
        headerShadowVisible: false,
        headerShown: route.name !== "index",
        headerTitleStyle: { color: theme.textPrimary },
        headerBackButtonDisplayMode: "minimal",
      })}
    >
      <Stack.Screen name="index" options={{ title }} />
    </Stack>
  );
}
