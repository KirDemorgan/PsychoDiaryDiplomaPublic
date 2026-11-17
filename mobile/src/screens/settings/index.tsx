import { Link, type Href } from "expo-router";
import { SymbolView, type SFSymbol } from "expo-symbols";
import { useState } from "react";
import { Alert, Pressable, StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import { sessionService } from "@/auth/session-service";
import { useSessionStore } from "@/auth/session-store";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { ScreenTitle } from "@/components/screen-title";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

interface SettingsRow {
  title: string;
  symbol: SFSymbol;
  href: Href;
}

const ROWS: SettingsRow[] = [
  { title: "Профиль", symbol: "person.crop.circle", href: "/settings/profile" },
  { title: "Препараты", symbol: "pills", href: "/medications" },
  { title: "AI и согласия", symbol: "sparkles", href: "/settings/ai" },
  { title: "Напоминания", symbol: "bell", href: "/settings/reminders" },
  { title: "Внешний вид", symbol: "circle.lefthalf.filled", href: "/settings/appearance" },
  { title: "Экспорт и удаление данных", symbol: "square.and.arrow.up", href: "/settings/data" },
  { title: "Знакомство с приложением", symbol: "info.circle", href: "/onboarding" },
];

export function SettingsScreen() {
  const theme = useTheme();
  const user = useSessionStore((state) => state.user);
  const [signingOut, setSigningOut] = useState(false);

  const signOut = async () => {
    setSigningOut(true);
    try {
      await sessionService.signOut();
    } catch (error) {
      setSigningOut(false);
      Alert.alert("Не удалось выйти", describeError(error));
    }
  };

  return (
    <Screen>
      <ScreenTitle>Настройки</ScreenTitle>
      {user ? (
        <View>
          <AppText variant="headline">{user.displayName ?? "Без имени"}</AppText>
          <AppText secondary>{user.email}</AppText>
        </View>
      ) : null}

      <Card style={styles.list}>
        {ROWS.map((row) => (
          <Link key={row.title} href={row.href} asChild>
            <Pressable accessibilityRole="link" style={styles.row}>
              <SymbolView name={row.symbol} tintColor={theme.primary} size={22} />
              <AppText style={styles.rowTitle}>{row.title}</AppText>
              <SymbolView name="chevron.right" tintColor={theme.textSecondary} size={14} />
            </Pressable>
          </Link>
        ))}
      </Card>

      <Button title="Выйти" variant="destructive" onPress={signOut} loading={signingOut} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { padding: 0, gap: 0 },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.md, padding: spacing.lg, minHeight: 52 },
  rowTitle: { flex: 1 },
});
