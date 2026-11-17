import { StyleSheet, type ScrollViewProps } from "react-native";

import { spacing } from "@/theme/tokens";

import { BackdropScroll } from "./backdrop-scroll";

export const TAB_BAR_CLEARANCE = 104;

export function Screen({ contentContainerStyle, ...rest }: ScrollViewProps) {
  return (
    <BackdropScroll
      contentInsetAdjustmentBehavior="automatic"
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="interactive"
      contentContainerStyle={[styles.content, contentContainerStyle]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.lg, paddingBottom: TAB_BAR_CLEARANCE, gap: spacing.lg, flexGrow: 1 },
});
