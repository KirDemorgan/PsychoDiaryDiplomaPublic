import { ActivityIndicator } from "react-native";

import type { Metric } from "@/api/analytics-types";
import { describeError } from "@/api/errors";
import type { TagCode } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { Screen } from "@/components/screen";
import { formatDecimal, METRIC_LABELS } from "@/features/analytics/metrics";
import { TAG_LABELS } from "@/features/tags/tag-colors";
import { useComparison } from "@/hooks/use-analytics";
import { joinSentences } from "@/lib/format";
import { deviceTimeZone } from "@/lib/time-zone";
import { useTheme } from "@/theme/use-theme";

const MIN_DAYS_PER_GROUP = 5;

interface ComparisonScreenProps {
  code: TagCode;
  metric: Metric;
  from: string;
  to: string;
}

export function ComparisonScreen({ code, metric, from, to }: ComparisonScreenProps) {
  const theme = useTheme();
  const comparison = useComparison(from, to, deviceTimeZone(), code, metric);
  const tag = TAG_LABELS[code];
  const metricName = METRIC_LABELS[metric].toLowerCase();

  if (comparison.isPending) {
    return (
      <Screen>
        <ActivityIndicator accessibilityLabel="Загрузка сравнения" />
      </Screen>
    );
  }
  if (comparison.isError) {
    return (
      <Screen>
        <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
          {describeError(comparison.error)}
        </AppText>
        <Button title="Повторить" variant="secondary" onPress={() => comparison.refetch()} />
      </Screen>
    );
  }

  const value = comparison.data;
  const sufficient = value.status === "SUFFICIENT";
  const excluded = value.excluded;
  const excludedTotal = excluded.noRating + excluded.noEntry + excluded.incompleteContext + excluded.ambiguousContext;

  return (
    <Screen>
      <AppText variant="title">
        {tag}: {metricName}
      </AppText>

      <Card>
        {sufficient ? (
          <>
            <AppText>
              В дни с подтверждённым упоминанием «{tag}» {metricName} в среднем {formatDecimal(value.withMention.mean)} из
              10 ({value.withMention.nDays} дн.), в дни без упоминания {formatDecimal(value.withoutMention.mean)} (
              {value.withoutMention.nDays} дн.). Разница {formatDecimal(value.delta)}.
            </AppText>
            <AppText variant="caption" secondary>
              Это связь, а не доказанная причина.
            </AppText>
          </>
        ) : (
          <AppText>
            Недостаточно данных для сравнения: нужно не менее {MIN_DAYS_PER_GROUP} дней в каждой группе. С упоминанием{" "}
            {value.withMention.nDays} дн., без упоминания {value.withoutMention.nDays} дн.
          </AppText>
        )}
      </Card>

      {excludedTotal > 0 ? (
        <Card>
          <AppText variant="headline">Не вошло в расчёт</AppText>
          {excluded.noRating > 0 ? <AppText variant="callout">Без оценки: {excluded.noRating} дн.</AppText> : null}
          {excluded.noEntry > 0 ? <AppText variant="callout">Без записей: {excluded.noEntry} дн.</AppText> : null}
          {excluded.incompleteContext > 0 ? (
            <AppText variant="callout">Записи не проверены полностью: {excluded.incompleteContext} дн.</AppText>
          ) : null}
          {excluded.ambiguousContext > 0 ? (
            <AppText variant="callout">Противоречивый контекст: {excluded.ambiguousContext} дн.</AppText>
          ) : null}
        </Card>
      ) : null}

      {value.limitations.length > 0 ? (
        <AppText variant="caption" secondary>
          {joinSentences(value.limitations)}
        </AppText>
      ) : null}
    </Screen>
  );
}
