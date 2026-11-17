import { Stack } from "expo-router";

import { DataScreen } from "@/screens/settings/data-screen";

export default function Route() {
  return (
    <>
      <Stack.Screen options={{ title: "Экспорт и удаление данных" }} />
      <DataScreen />
    </>
  );
}
