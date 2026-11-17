import { Link } from "expo-router";
import { ActivityIndicator, Pressable, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { ScreenTitle } from "@/components/screen-title";
import { useProfile } from "@/hooks/use-profile";
import { formatLongDate, greeting } from "@/lib/format";
import { useTheme } from "@/theme/use-theme";

import { OccurrencesCard } from "./occurrences-card";
import { RatingsCard } from "./ratings-card";

export function TodayScreen() {
  const theme = useTheme();
  const profile = useProfile();

  return (
    <Screen>
      <ScreenTitle>Сегодня</ScreenTitle>
      <View style={{ gap: 2 }}>
        {profile.isPending ? (
          <ActivityIndicator accessibilityLabel="Загрузка профиля" />
        ) : profile.isError ? null : (
          <AppText variant="title">{greeting(profile.data.displayName)}</AppText>
        )}
        <AppText secondary>{formatLongDate(new Date())}</AppText>
      </View>

      {profile.isError ? (
        <Card>
          <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
            {describeError(profile.error)}
          </AppText>
          <Button title="Повторить" variant="secondary" onPress={() => profile.refetch()} />
        </Card>
      ) : null}

      <RatingsCard />

      <Link href="/entry/new" asChild>
        <Pressable accessibilityRole="link" accessibilityLabel="Записать мысль">
          <Card>
            <AppText variant="headline">Записать мысль</AppText>
            <AppText secondary>Что произошло сегодня?</AppText>
          </Card>
        </Pressable>
      </Link>

      <OccurrencesCard />
    </Screen>
  );
}
