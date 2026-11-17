import { useLocalSearchParams } from "expo-router";

import { ReviewScreen } from "@/screens/review/review-screen";

export default function ReviewRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <ReviewScreen entryId={id} />;
}
