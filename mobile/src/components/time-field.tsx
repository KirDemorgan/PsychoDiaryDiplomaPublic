import DateTimePicker from "@react-native-community/datetimepicker";
import { StyleSheet, View } from "react-native";

import { dateToTime, timeToDate } from "@/features/medications/schedule-form";

import { AppText } from "./app-text";

interface TimeFieldProps {
  label: string;
  value: string;
  onChange: (time: string) => void;
}

export function TimeField({ label, value, onChange }: TimeFieldProps) {
  return (
    <View style={styles.row}>
      <AppText variant="callout">{label}</AppText>
      <DateTimePicker
        accessibilityLabel={label}
        mode="time"
        display="compact"
        value={timeToDate(value)}
        onChange={(_event, date) => {
          if (date) {
            onChange(dateToTime(date));
          }
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
});
