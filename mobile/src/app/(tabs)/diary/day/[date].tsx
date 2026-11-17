import { Stack, useLocalSearchParams } from "expo-router";

import { DayScreen } from "@/screens/calendar/day-screen";

export default function DayRoute() {
  const { date } = useLocalSearchParams<{ date: string }>();
  return (
    <>
      <Stack.Screen options={{ title: "День" }} />
      <DayScreen date={date} />
    </>
  );
}
