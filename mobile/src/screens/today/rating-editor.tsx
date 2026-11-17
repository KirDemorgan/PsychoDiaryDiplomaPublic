import { randomUUID } from "expo-crypto";
import { useRef, useState } from "react";
import { View } from "react-native";

import { isApiError, describeError } from "@/api/errors";
import type { RatingResponse, RatingSlot, RatingValues } from "@/api/ratings-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { RatingSlider } from "@/components/rating-slider";
import { hasAnyValue, sameValues, valuesOf } from "@/features/ratings/rating-values";
import { useSaveRating } from "@/hooks/use-save-rating";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

interface RatingEditorProps {
  date: string;
  slot: RatingSlot;
  timeZone: string;
  existing: RatingResponse | undefined;
}

const VERSION_CONFLICT_MESSAGE = "Оценка уже изменена на другом устройстве. Данные обновлены, проверьте и сохраните снова.";

export function RatingEditor({ date, slot, timeZone, existing }: RatingEditorProps) {
  const theme = useTheme();
  const saved = valuesOf(existing);
  const [draft, setDraft] = useState<RatingValues>(saved);
  const [error, setError] = useState<string | null>(null);
  const [initialKey] = useState(randomUUID);
  const idempotencyKey = useRef(initialKey);
  const { save, remove } = useSaveRating(date);

  const busy = save.isPending || remove.isPending;
  const canSubmit = !busy && !sameValues(draft, saved) && (hasAnyValue(draft) || existing !== undefined);

  const submit = async () => {
    setError(null);
    try {
      if (hasAnyValue(draft)) {
        await save.mutateAsync({ date, slot, values: draft, timeZone, existing, idempotencyKey: idempotencyKey.current });
      } else if (existing) {
        await remove.mutateAsync({ date, slot, existing });
      }
    } catch (failure) {
      setError(isApiError(failure) && failure.status === 412 ? VERSION_CONFLICT_MESSAGE : describeError(failure));
    }
  };

  const update = (key: keyof RatingValues) => (value: number | null) => setDraft((current) => ({ ...current, [key]: value }));

  return (
    <View style={{ gap: spacing.lg }}>
      <RatingSlider label="Тревожность" color={theme.anxiety} value={draft.anxiety} onChange={update("anxiety")} />
      <RatingSlider label="Настроение" color={theme.mood} value={draft.mood} onChange={update("mood")} />
      <RatingSlider label="Энергия" color={theme.energy} value={draft.energy} onChange={update("energy")} />

      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}

      <Button
        title={existing ? "Обновить оценку" : "Сохранить оценку"}
        onPress={() => {
          void submit();
        }}
        disabled={!canSubmit}
        loading={busy}
      />
    </View>
  );
}
