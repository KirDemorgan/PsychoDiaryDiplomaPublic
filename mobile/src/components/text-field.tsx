import { StyleSheet, TextInput, View, type TextInputProps } from "react-native";

import { radius, spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { AppText } from "./app-text";

interface TextFieldProps extends TextInputProps {
  label: string;
  error?: string;
}

export function TextField({ label, error, style, ...rest }: TextFieldProps) {
  const theme = useTheme();
  return (
    <View style={styles.wrapper}>
      <AppText variant="caption" secondary>
        {label}
      </AppText>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={theme.textSecondary}
        style={[
          styles.input,
          { backgroundColor: theme.surface, color: theme.textPrimary, borderColor: error ? theme.danger : theme.border },
          style,
        ]}
        {...rest}
      />
      {error ? (
        <AppText variant="caption" accessibilityRole="alert" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { gap: spacing.xs },
  input: {
    minHeight: 50,
    borderRadius: radius.field,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: spacing.lg,
    fontSize: 17,
    borderCurve: "continuous",
  },
});
