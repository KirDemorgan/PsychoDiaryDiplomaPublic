import { Link } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, View } from "react-native";

import { describeError } from "@/api/errors";
import type { EntryResponse } from "@/api/entries-types";
import type { AnalysisResponse } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { PRESENCE_LABELS } from "@/features/tags/labels";
import { TAG_LABELS } from "@/features/tags/tag-colors";
import { useAnalysis, useStartAnalysis } from "@/hooks/use-analysis";
import { useEntry } from "@/hooks/use-entries";
import { useSettings } from "@/hooks/use-settings";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { CandidateReview } from "./candidate-review";
import { ManualTagging } from "./manual-tagging";

const FAILURE_TEXT = "Не удалось получить теги от AI. Запись сохранена, анализ можно повторить.";

export function ReviewScreen({ entryId }: { entryId: string }) {
  const theme = useTheme();
  const entry = useEntry(entryId);
  const settings = useSettings();
  const latest = entry.data?.latestAnalysis ?? null;
  const analysisQuery = useAnalysis(entryId, latest?.id);
  const start = useStartAnalysis(entryId);
  const [manual, setManual] = useState(false);

  if (entry.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка записи" />
      </Screen>
    );
  }
  if (entry.isError) {
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {describeError(entry.error)}
        </AppText>
        <Button title="Повторить" variant="secondary" onPress={() => entry.refetch()} />
      </Screen>
    );
  }

  const value = entry.data;
  const analysis: AnalysisResponse | null = analysisQuery.data ?? latest;
  const current = analysis !== null && analysis.revision === value.revision ? analysis : null;

  if (value.reviewComplete) {
    return <ConfirmedTags entry={value} />;
  }

  if (current?.status === "SUCCEEDED") {
    return (
      <Screen>
        <CandidateReview key={current.id} entry={value} candidates={current.result?.candidates ?? []} />
      </Screen>
    );
  }

  if (manual) {
    return (
      <Screen>
        <ManualTagging entry={value} />
      </Screen>
    );
  }

  const working = current?.status === "QUEUED" || current?.status === "RUNNING";
  const aiOff = settings.isSuccess && !settings.data.aiEnabled;
  const failed = current !== null && !working;

  return (
    <Screen>
      <Card>
        <AppText variant="headline">Ваша запись</AppText>
        <AppText>{value.text}</AppText>
      </Card>

      {working ? (
        <Card style={{ alignItems: "center" }}>
          <ActivityIndicator accessibilityLabel="AI анализирует запись" />
          <AppText secondary>AI ищет теги в записи…</AppText>
        </Card>
      ) : aiOff ? (
        <Card>
          <AppText>AI выключен. Включите его в настройках, чтобы получить предложенные теги.</AppText>
          <Link href="/settings/ai" asChild>
            <Button title="Открыть настройки AI" variant="secondary" />
          </Link>
        </Card>
      ) : (
        <View style={{ gap: spacing.md }}>
          {failed ? (
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              {FAILURE_TEXT}
            </AppText>
          ) : null}
          {start.isError ? (
            <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
              {describeError(start.error)}
            </AppText>
          ) : null}
          {aiOff ? null : (
            <Button
              title={failed ? "Повторить анализ" : "Запустить AI-анализ"}
              onPress={() => start.mutate({ revision: value.revision })}
              loading={start.isPending}
            />
          )}
        </View>
      )}

      {working ? null : <Button title="Разметить вручную" variant="secondary" onPress={() => setManual(true)} />}
    </Screen>
  );
}

function ConfirmedTags({ entry }: { entry: EntryResponse }) {
  return (
    <Screen>
      <Card>
        <AppText variant="headline">Теги подтверждены</AppText>
        {entry.tags.length === 0 ? <AppText secondary>Без тегов.</AppText> : null}
        {entry.tags.map((tag) => (
          <AppText key={tag.id}>
            {TAG_LABELS[tag.labelCode]} · {PRESENCE_LABELS[tag.presence]}
            {tag.quote ? ` · «${tag.quote}»` : ""}
          </AppText>
        ))}
      </Card>
    </Screen>
  );
}
