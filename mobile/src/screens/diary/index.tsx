import { useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, FlatList, StyleSheet, View } from "react-native";

import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { TextField } from "@/components/text-field";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useEntries } from "@/hooks/use-entries";
import { BackdropScroll } from "@/components/backdrop-scroll";
import { TAB_BAR_CLEARANCE } from "@/components/screen";
import { ScreenTitle } from "@/components/screen-title";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { EntryRow } from "./entry-row";

const SEARCH_DELAY_MS = 350;

export function DiaryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search, SEARCH_DELAY_MS);
  const entries = useEntries(debouncedSearch);
  const items = entries.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <FlatList
      data={items}
      keyExtractor={(entry) => entry.id}
      renderItem={({ item }) => <EntryRow entry={item} />}
      contentInsetAdjustmentBehavior="automatic"
      keyboardDismissMode="on-drag"
      keyboardShouldPersistTaps="handled"
      renderScrollComponent={(props) => <BackdropScroll {...props} />}
      contentContainerStyle={styles.content}
      refreshing={entries.isRefetching && !entries.isFetchingNextPage}
      onRefresh={() => entries.refetch()}
      onEndReached={() => {
        if (entries.hasNextPage && !entries.isFetchingNextPage) {
          void entries.fetchNextPage();
        }
      }}
      ListHeaderComponent={
        <View style={styles.header}>
          <ScreenTitle>Дневник</ScreenTitle>
          <Button title="Новая запись" onPress={() => router.push("/entry/new")} />
          <Button title="Календарь" variant="secondary" onPress={() => router.push("/diary/calendar")} />
          <TextField
            label="Поиск по записям"
            value={search}
            onChangeText={setSearch}
            returnKeyType="search"
            autoCorrect={false}
          />
        </View>
      }
      ListEmptyComponent={
        entries.isPending ? (
          <ActivityIndicator accessibilityLabel="Загрузка записей" />
        ) : entries.isError ? (
          <View style={styles.message}>
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              {describeError(entries.error)}
            </AppText>
            <Button title="Повторить" variant="secondary" onPress={() => entries.refetch()} />
          </View>
        ) : (
          <AppText secondary style={styles.empty}>
            {debouncedSearch.trim() ? "Ничего не найдено." : "Записей пока нет. Добавьте первую."}
          </AppText>
        )
      }
      ListFooterComponent={entries.isFetchingNextPage ? <ActivityIndicator /> : undefined}
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: TAB_BAR_CLEARANCE, flexGrow: 1 },
  header: { gap: spacing.lg, marginBottom: spacing.lg },
  message: { gap: spacing.md },
  empty: { textAlign: "center", marginTop: spacing.xl },
});
