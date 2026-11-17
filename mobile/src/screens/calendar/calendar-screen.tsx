import { useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";

import type { CalendarDay } from "@/api/calendar-types";
import { describeError } from "@/api/errors";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { monthBounds, monthGrid, monthOf, monthTitle, shiftMonth, WEEKDAY_LABELS } from "@/features/calendar/month";
import { useCalendar } from "@/hooks/use-calendar";
import { localDateIn } from "@/lib/dates";
import { deviceTimeZone } from "@/lib/time-zone";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

const ratedCount = (day: CalendarDay) => Object.values(day.ratings).filter(Boolean).length;

function describeDay(day: CalendarDay | undefined): string {
  if (!day) {
    return "нет данных";
  }
  const parts: string[] = [];
  const ratings = ratedCount(day);
  if (ratings > 0) parts.push(`оценок: ${ratings}`);
  if (day.entryCount > 0) parts.push(`записей: ${day.entryCount}`);
  if (day.pendingReviewCount > 0) parts.push(`ждут проверки: ${day.pendingReviewCount}`);
  return parts.length > 0 ? parts.join(", ") : "нет данных";
}

export function CalendarScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [timeZone] = useState(deviceTimeZone);
  const [today] = useState(() => localDateIn(timeZone));
  const [month, setMonth] = useState(() => monthOf(today));
  const bounds = monthBounds(month);
  const calendar = useCalendar(bounds.from, bounds.to, timeZone);
  const byDate = new Map(calendar.data?.days.map((day) => [day.date, day]));

  return (
    <Screen>
      <View style={styles.header}>
        <Button title="‹" variant="secondary" accessibilityLabel="Предыдущий месяц" onPress={() => setMonth(shiftMonth(month, -1))} />
        <AppText variant="headline" accessibilityRole="header">
          {monthTitle(month)}
        </AppText>
        <Button title="›" variant="secondary" accessibilityLabel="Следующий месяц" onPress={() => setMonth(shiftMonth(month, 1))} />
      </View>

      <Card>
        <View style={styles.week}>
          {WEEKDAY_LABELS.map((label) => (
            <AppText key={label} variant="caption" secondary style={styles.cell}>
              {label}
            </AppText>
          ))}
        </View>
        {monthGrid(month).map((week, index) => (
          <View key={index} style={styles.week}>
            {week.map((date, position) => {
              if (date === null) {
                return <View key={position} style={styles.cell} />;
              }
              const day = byDate.get(date);
              const hasData = day !== undefined && (ratedCount(day) > 0 || day.entryCount > 0);
              return (
                <Pressable
                  key={date}
                  accessibilityRole="link"
                  accessibilityLabel={`${Number(date.slice(8))} число, ${describeDay(day)}`}
                  onPress={() => router.push({ pathname: "/diary/day/[date]", params: { date } })}
                  style={[styles.cell, date === today && { borderColor: theme.primary, borderWidth: 1.5, borderRadius: 12 }]}
                >
                  <AppText variant="callout">{Number(date.slice(8))}</AppText>
                  <View
                    style={[
                      styles.dot,
                      { backgroundColor: day && day.pendingReviewCount > 0 ? theme.tagAnxiety : hasData ? theme.primary : "transparent" },
                    ]}
                  />
                </Pressable>
              );
            })}
          </View>
        ))}
      </Card>

      {calendar.isPending ? <ActivityIndicator accessibilityLabel="Загрузка календаря" /> : null}
      {calendar.isError ? (
        <>
          <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
            {describeError(calendar.error)}
          </AppText>
          <Button title="Повторить" variant="secondary" onPress={() => calendar.refetch()} />
        </>
      ) : null}

      <AppText variant="caption" secondary>
        Бирюзовая точка: есть оценки или записи. Оранжевая: есть записи, ожидающие проверки тегов.
      </AppText>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.md },
  week: { flexDirection: "row" },
  cell: { flex: 1, minHeight: 44, alignItems: "center", justifyContent: "center", gap: 2, textAlign: "center" },
  dot: { width: 6, height: 6, borderRadius: 3 },
});
