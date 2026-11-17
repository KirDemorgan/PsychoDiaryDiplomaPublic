import { useState } from "react";

import { describeError } from "@/api/errors";
import { useSessionStore } from "@/auth/session-store";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { useUpdateProfile } from "@/hooks/use-account";
import { deviceTimeZone } from "@/lib/time-zone";
import { useTheme } from "@/theme/use-theme";

const NAME_MAX_LENGTH = 100;

export function ProfileScreen() {
  const theme = useTheme();
  const user = useSessionStore((state) => state.user);
  const update = useUpdateProfile();
  const [name, setName] = useState(user?.displayName ?? "");
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const device = deviceTimeZone();

  if (!user) {
    return null;
  }

  const trimmed = name.trim();
  const nameChanged = trimmed !== (user.displayName ?? "");
  const zoneDiffers = user.timeZone !== device;

  const save = async (body: { displayName?: string | null; timeZone?: string }) => {
    setError(null);
    setSaved(false);
    try {
      await update.mutateAsync({ version: user.version, body });
      setSaved(true);
    } catch (failure) {
      setError(describeError(failure));
    }
  };

  return (
    <Screen>
      <AppText secondary>{user.email}</AppText>
      <TextField
        label="Имя или псевдоним"
        value={name}
        onChangeText={(value) => {
          setName(value);
          setSaved(false);
        }}
        error={name.trim().length > NAME_MAX_LENGTH ? "Не более 100 символов" : undefined}
      />
      <Button
        title="Сохранить"
        disabled={!nameChanged || trimmed.length > NAME_MAX_LENGTH}
        loading={update.isPending}
        onPress={() => {
          void save({ displayName: trimmed === "" ? null : trimmed });
        }}
      />

      <AppText variant="caption" secondary>
        Часовой пояс профиля: {user.timeZone}
      </AppText>
      {zoneDiffers ? (
        <Button
          title={`Использовать пояс устройства (${device})`}
          variant="secondary"
          disabled={update.isPending}
          onPress={() => {
            void save({ timeZone: device });
          }}
        />
      ) : null}

      {saved ? <AppText variant="callout">Сохранено.</AppText> : null}
      {error ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {error}
        </AppText>
      ) : null}
    </Screen>
  );
}
