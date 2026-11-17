import { Stack } from "expo-router";

import { RemindersScreen } from "@/screens/settings/reminders-screen";

export default function RemindersRoute() {
  return (
    <>
      <Stack.Screen options={{ title: "Напоминания" }} />
      <RemindersScreen />
    </>
  );
}
