import { useRouter } from "expo-router";
import { useState } from "react";
import { View } from "react-native";

import { describeError, isApiError } from "@/api/errors";
import type { EntryResponse } from "@/api/entries-types";
import type { Candidate } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { HighlightedText } from "@/components/highlighted-text";
import {
  applyEdit,
  buildReviewRequest,
  countAccepted,
  initialDecisions,
  toggleDecision,
  type CandidateEdit,
  type DecisionMap,
} from "@/features/tags/review-state";
import { tagColor } from "@/features/tags/tag-colors";
import { useSubmitTagReview } from "@/hooks/use-analysis";
import { localDateIn } from "@/lib/dates";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { CandidateCard } from "./candidate-card";
import { CandidateEditor } from "./candidate-editor";

const STALE_MESSAGE = "Запись изменилась. Данные обновлены, запустите анализ заново.";

interface CandidateReviewProps {
  entry: EntryResponse;
  candidates: Candidate[];
}

export function CandidateReview({ entry, candidates }: CandidateReviewProps) {
  const theme = useTheme();
  const router = useRouter();
  const submit = useSubmitTagReview(entry.id);
  const [decisions, setDecisions] = useState<DecisionMap>(() => initialDecisions(candidates));
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const localDate = localDateIn(entry.timeZone, new Date(entry.occurredAt));

  const confirm = async () => {
    setError(null);
    try {
      await submit.mutateAsync({
        version: entry.version,
        body: buildReviewRequest(entry.revision, candidates, decisions, localDate),
      });
      router.back();
    } catch (failure) {
      setError(isApiError(failure) && failure.code === "STALE_REVISION" ? STALE_MESSAGE : describeError(failure));
    }
  };

  const spans = candidates.map((candidate) => {
    const decision = decisions[candidate.candidateId];
    const shown = decision?.action === "EDIT" ? decision.replacement : candidate;
    return { id: candidate.candidateId, start: candidate.start, end: candidate.end, color: tagColor(shown.labelCode, theme) };
  });

  return (
    <View style={{ gap: spacing.lg }}>
      <Card>
        <AppText variant="headline">Ваша запись</AppText>
        <HighlightedText text={entry.text} spans={spans.filter((span) => decisions[span.id]?.action !== "REJECT")} />
      </Card>

      <AppText variant="headline">Предложенные теги</AppText>
      {candidates.length === 0 ? (
        <AppText secondary>AI не нашёл подходящих признаков. Можно завершить проверку без тегов.</AppText>
      ) : null}

      {candidates.map((candidate) =>
        editingId === candidate.candidateId ? (
          <Card key={candidate.candidateId}>
            <CandidateEditor
              candidate={candidate}
              initial={editInitial(candidate, decisions[candidate.candidateId])}
              onCancel={() => setEditingId(null)}
              onSave={(edit) => {
                setDecisions((current) => applyEdit(current, candidate, edit, localDate));
                setEditingId(null);
              }}
            />
          </Card>
        ) : (
          <CandidateCard
            key={candidate.candidateId}
            candidate={candidate}
            decision={decisions[candidate.candidateId]}
            onToggle={() => setDecisions((current) => toggleDecision(current, candidate.candidateId))}
            onEdit={() => setEditingId(candidate.candidateId)}
          />
        ),
      )}

      <AppText variant="caption" secondary>
        Подтвердите или исправьте теги. В аналитику попадают только подтверждённые.
      </AppText>

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Button
        title={countAccepted(decisions) === 0 ? "Завершить без тегов" : "Подтвердить теги"}
        onPress={() => {
          void confirm();
        }}
        loading={submit.isPending}
        disabled={editingId !== null}
      />
    </View>
  );
}

function editInitial(candidate: Candidate, decision: DecisionMap[string] | undefined): CandidateEdit {
  const source = decision?.action === "EDIT" ? decision.replacement : candidate;
  return { labelCode: source.labelCode, presence: source.presence, temporal: source.temporal };
}
