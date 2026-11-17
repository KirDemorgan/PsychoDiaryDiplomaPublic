import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { describeError } from "@/api/errors";
import { sessionService } from "@/auth/session-service";
import { signUpSchema, type SignUpForm } from "@/auth/validation";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { deviceTimeZone } from "@/lib/time-zone";
import { useTheme } from "@/theme/use-theme";

export function SignUpScreen() {
  const theme = useTheme();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SignUpForm>({
    resolver: zodResolver(signUpSchema),
    defaultValues: { displayName: "", email: "", password: "" },
  });

  const submit = handleSubmit(async ({ displayName, email, password }) => {
    setSubmitError(null);
    try {
      await sessionService.signUp({
        email,
        password,
        displayName: displayName || undefined,
        timeZone: deviceTimeZone(),
      });
    } catch (error) {
      setSubmitError(describeError(error));
    }
  });

  const onSubmit = () => {
    void submit();
  };

  return (
    <Screen>
      <Controller
        control={control}
        name="displayName"
        render={({ field: { onChange, onBlur, value } }) => (
          <TextField
            label="Имя или псевдоним (необязательно)"
            value={value}
            onChangeText={onChange}
            onBlur={onBlur}
            error={errors.displayName?.message}
            autoComplete="name"
            textContentType="name"
            returnKeyType="next"
          />
        )}
      />
      <Controller
        control={control}
        name="email"
        render={({ field: { onChange, onBlur, value } }) => (
          <TextField
            label="Email"
            value={value}
            onChangeText={onChange}
            onBlur={onBlur}
            error={errors.email?.message}
            autoCapitalize="none"
            autoComplete="email"
            autoCorrect={false}
            keyboardType="email-address"
            textContentType="username"
            returnKeyType="next"
          />
        )}
      />
      <Controller
        control={control}
        name="password"
        render={({ field: { onChange, onBlur, value } }) => (
          <TextField
            label="Пароль"
            value={value}
            onChangeText={onChange}
            onBlur={onBlur}
            error={errors.password?.message}
            secureTextEntry
            autoCapitalize="none"
            autoComplete="new-password"
            textContentType="newPassword"
            returnKeyType="go"
            onSubmitEditing={onSubmit}
          />
        )}
      />
      <AppText variant="caption" secondary>
        Минимум 12 символов. Данные дневника хранятся на вашем сервере, AI включается отдельно в настройках.
      </AppText>

      {submitError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {submitError}
        </AppText>
      ) : null}

      <Button title="Создать аккаунт" onPress={onSubmit} loading={isSubmitting} />
    </Screen>
  );
}
