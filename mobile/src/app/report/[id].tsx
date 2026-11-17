import { useLocalSearchParams } from "expo-router";

import { ReportDetailScreen } from "@/screens/report/report-detail-screen";

export default function ReportRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <ReportDetailScreen id={id} />;
}
