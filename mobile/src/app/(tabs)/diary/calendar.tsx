import { Stack } from "expo-router";

import { CalendarScreen } from "@/screens/calendar/calendar-screen";

export default function CalendarRoute() {
  return (
    <>
      <Stack.Screen options={{ title: "Календарь" }} />
      <CalendarScreen />
    </>
  );
}
