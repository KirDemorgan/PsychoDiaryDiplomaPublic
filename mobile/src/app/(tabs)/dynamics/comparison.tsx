import { Stack, useLocalSearchParams } from "expo-router";

import type { Metric } from "@/api/analytics-types";
import type { TagCode } from "@/api/tags-types";
import { ComparisonScreen } from "@/screens/dynamics/comparison-screen";

export default function ComparisonRoute() {
  const params = useLocalSearchParams<{ code: TagCode; metric: Metric; from: string; to: string }>();
  return (
    <>
      <Stack.Screen options={{ title: "Сравнение дней" }} />
      <ComparisonScreen code={params.code} metric={params.metric} from={params.from} to={params.to} />
    </>
  );
}
