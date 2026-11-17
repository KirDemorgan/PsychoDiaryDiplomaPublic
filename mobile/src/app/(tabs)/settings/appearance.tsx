import { Stack } from "expo-router";

import { AppearanceScreen } from "@/screens/settings/appearance-screen";

export default function Route() {
  return (
    <>
      <Stack.Screen options={{ title: "Внешний вид" }} />
      <AppearanceScreen />
    </>
  );
}
