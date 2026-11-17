import { useLocalSearchParams } from "expo-router";

import { MedicationDetailScreen } from "@/screens/medications/medication-detail-screen";

export default function MedicationRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <MedicationDetailScreen id={id} />;
}
