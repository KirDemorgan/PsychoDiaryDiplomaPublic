import DateTimePicker from "@react-native-community/datetimepicker";
import { StyleSheet, View } from "react-native";

import { AppText } from "./app-text";

interface DateFieldProps {
  label: string;
  value: Date;
  minimumDate?: Date;
  onChange: (date: Date) => void;
}

export function DateField({ label, value, minimumDate, onChange }: DateFieldProps) {
  return (
    <View style={styles.row}>
      <AppText variant="callout">{label}</AppText>
      <DateTimePicker
        accessibilityLabel={label}
        mode="date"
        display="compact"
        value={value}
        minimumDate={minimumDate}
        onChange={(_event, date) => {
          if (date) {
            onChange(date);
          }
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
});
