import { Stack } from "expo-router";

import { AiSettingsScreen } from "@/screens/settings/ai-settings-screen";

export default function AiSettingsRoute() {
  return (
    <>
      <Stack.Screen options={{ title: "AI и согласия" }} />
      <AiSettingsScreen />
    </>
  );
}
