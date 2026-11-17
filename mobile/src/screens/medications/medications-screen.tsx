import { useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, FlatList, StyleSheet, Switch, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { useMedications } from "@/hooks/use-medications";
import { BackdropScroll } from "@/components/backdrop-scroll";
import { TAB_BAR_CLEARANCE } from "@/components/screen";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { MedicationCard } from "./medication-card";

export function MedicationsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [archived, setArchived] = useState(false);
  const medications = useMedications(archived);

  return (
    <FlatList
      data={medications.data ?? []}
      keyExtractor={(medication) => medication.id}
      renderItem={({ item }) => <MedicationCard medication={item} />}
      contentInsetAdjustmentBehavior="automatic"
      renderScrollComponent={(props) => <BackdropScroll {...props} />}
      contentContainerStyle={styles.content}
      refreshing={medications.isRefetching}
      onRefresh={() => medications.refetch()}
      ListHeaderComponent={
        <View style={styles.header}>
          <Button title="Добавить препарат" onPress={() => router.push("/medications/new")} />
          <View style={styles.row}>
            <AppText>Показать архивные</AppText>
            <Switch
              accessibilityLabel="Показать архивные"
              value={archived}
              onValueChange={setArchived}
              trackColor={{ true: theme.primary }}
            />
          </View>
        </View>
      }
      ListEmptyComponent={
        medications.isPending ? (
          <ActivityIndicator accessibilityLabel="Загрузка препаратов" />
        ) : medications.isError ? (
          <View style={styles.header}>
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              {describeError(medications.error)}
            </AppText>
            <Button title="Повторить" variant="secondary" onPress={() => medications.refetch()} />
          </View>
        ) : (
          <AppText secondary style={styles.empty}>
            {archived ? "Архивных препаратов нет." : "Препараты не добавлены."}
          </AppText>
        )
      }
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: TAB_BAR_CLEARANCE, flexGrow: 1 },
  header: { gap: spacing.lg, marginBottom: spacing.lg },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  empty: { textAlign: "center", marginTop: spacing.xl },
});
