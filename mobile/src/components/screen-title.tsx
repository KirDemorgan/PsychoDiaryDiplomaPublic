import { AppText } from "./app-text";

export function ScreenTitle({ children }: { children: string }) {
  return (
    <AppText variant="largeTitle" accessibilityRole="header">
      {children}
    </AppText>
  );
}
