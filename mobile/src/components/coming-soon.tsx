import { StyleSheet, View } from "react-native";

import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";

interface ComingSoonProps {
  title: string;
  description?: string;
}

export function ComingSoon({ title, description = "Раздел появится в одном из следующих шагов." }: ComingSoonProps) {
  const theme = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <AppText variant="title">{title}</AppText>
      <AppText secondary style={styles.description}>
        {description}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.sm, padding: spacing.xl },
  description: { textAlign: "center" },
});
