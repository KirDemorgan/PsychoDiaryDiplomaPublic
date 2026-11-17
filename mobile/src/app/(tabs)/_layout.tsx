import { useSessionStore } from "@/auth/session-store";
import { usePendingQuickAction } from "@/features/quick-actions/use-pending-quick-action";
import { useTheme } from "@/theme/use-theme";
import { NativeTabs } from "expo-router/native-tabs";

export default function TabsLayout() {
  const theme = useTheme();
  usePendingQuickAction(useSessionStore((state) => state.status));
  const content = { backgroundColor: theme.background };
  return (
    <NativeTabs>
      <NativeTabs.Trigger contentStyle={content} name="today">
        <NativeTabs.Trigger.Icon sf="sun.max" />
        <NativeTabs.Trigger.Label>Сегодня</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger contentStyle={content} name="diary">
        <NativeTabs.Trigger.Icon sf="doc.text" />
        <NativeTabs.Trigger.Label>Дневник</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger contentStyle={content} name="dynamics">
        <NativeTabs.Trigger.Icon sf="chart.bar" />
        <NativeTabs.Trigger.Label>Динамика</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger contentStyle={content} name="settings">
        <NativeTabs.Trigger.Icon sf="gearshape" />
        <NativeTabs.Trigger.Label>Настройки</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
