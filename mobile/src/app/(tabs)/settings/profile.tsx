import { Stack } from "expo-router";

import { ProfileScreen } from "@/screens/settings/profile-screen";

export default function Route() {
  return (
    <>
      <Stack.Screen options={{ title: "Профиль" }} />
      <ProfileScreen />
    </>
  );
}
