import { useState } from "react";
import { ActivityIndicator } from "react-native";

import { describeError } from "@/api/errors";
import type { RatingSlot } from "@/api/ratings-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Card } from "@/components/card";
import { SegmentedPicker } from "@/components/segmented-picker";
import { SLOT_OPTIONS } from "@/features/ratings/slot-labels";
import { useDayRatings } from "@/hooks/use-day-ratings";
import { currentSlot, localDateIn } from "@/lib/dates";
import { deviceTimeZone } from "@/lib/time-zone";
import { useTheme } from "@/theme/use-theme";

import { RatingEditor } from "./rating-editor";

export function RatingsCard() {
  const theme = useTheme();
  const [timeZone] = useState(deviceTimeZone);
  const [date] = useState(() => localDateIn(timeZone));
  const [slot, setSlot] = useState<RatingSlot>(() => currentSlot(timeZone));
  const ratings = useDayRatings(date);
  const existing = ratings.data?.find((rating) => rating.slot === slot);

  return (
    <Card>
      <AppText variant="headline">Как вы себя чувствуете?</AppText>
      <SegmentedPicker accessibilityLabel="Время суток" options={SLOT_OPTIONS} value={slot} onChange={setSlot} />

      {ratings.isPending ? <ActivityIndicator accessibilityLabel="Загрузка оценок" /> : null}

      {ratings.isError ? (
        <>
          <AppText accessibilityRole="alert" style={{ color: theme.danger }}>
            {describeError(ratings.error)}
          </AppText>
          <Button title="Повторить" variant="secondary" onPress={() => ratings.refetch()} />
        </>
      ) : null}

      {ratings.isSuccess ? (
        <RatingEditor
          key={`${slot}-${existing?.version ?? "new"}`}
          date={date}
          slot={slot}
          timeZone={timeZone}
          existing={existing}
        />
      ) : null}
    </Card>
  );
}
