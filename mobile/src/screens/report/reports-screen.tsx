import { Link, useRouter } from "expo-router";
import { ActivityIndicator, FlatList, Pressable, StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { STATUS_LABELS } from "@/features/reports/labels";
import { useReports } from "@/hooks/use-reports";
import { formatDateOnly } from "@/lib/format";
import { BackdropScroll } from "@/components/backdrop-scroll";
import { TAB_BAR_CLEARANCE } from "@/components/screen";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export function ReportsScreen() {
  const theme = useTheme();
  const router = useRouter();
  const reports = useReports();

  return (
    <FlatList
      data={reports.data ?? []}
      keyExtractor={(report) => report.id}
      contentInsetAdjustmentBehavior="automatic"
      renderScrollComponent={(props) => <BackdropScroll {...props} />}
      contentContainerStyle={styles.content}
      refreshing={reports.isRefetching}
      onRefresh={() => reports.refetch()}
      ListHeaderComponent={
        <View style={styles.header}>
          <Button title="Новый отчёт" onPress={() => router.push("/report/new")} />
        </View>
      }
      renderItem={({ item }) => (
        <Link href={{ pathname: "/report/[id]", params: { id: item.id } }} asChild>
          <Pressable accessibilityRole="link" style={styles.item}>
            <Card style={styles.card}>
              <AppText variant="headline">
                {formatDateOnly(item.from)} — {formatDateOnly(item.to)}
              </AppText>
              <AppText variant="caption" secondary>
                {STATUS_LABELS[item.status]}
                {item.stale ? " · устарел" : ""}
              </AppText>
            </Card>
          </Pressable>
        </Link>
      )}
      ListEmptyComponent={
        reports.isPending ? (
          <ActivityIndicator accessibilityLabel="Загрузка отчётов" />
        ) : reports.isError ? (
          <View style={styles.header}>
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              {describeError(reports.error)}
            </AppText>
            <Button title="Повторить" variant="secondary" onPress={() => reports.refetch()} />
          </View>
        ) : (
          <AppText secondary style={styles.empty}>
            Отчётов пока нет.
          </AppText>
        )
      }
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: TAB_BAR_CLEARANCE, flexGrow: 1 },
  header: { gap: spacing.lg, marginBottom: spacing.lg },
  item: { marginBottom: spacing.md },
  card: { gap: spacing.xs },
  empty: { textAlign: "center", marginTop: spacing.xl },
});
