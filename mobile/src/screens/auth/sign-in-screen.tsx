import { zodResolver } from "@hookform/resolvers/zod";
import { Link } from "expo-router";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { View } from "react-native";

import { describeError } from "@/api/errors";
import { sessionService } from "@/auth/session-service";
import { signInSchema, type SignInForm } from "@/auth/validation";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Screen } from "@/components/screen";
import { TextField } from "@/components/text-field";
import { useTheme } from "@/theme/use-theme";

export function SignInScreen() {
  const theme = useTheme();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SignInForm>({
    resolver: zodResolver(signInSchema),
    defaultValues: { email: "", password: "" },
  });

  const submit = handleSubmit(async ({ email, password }) => {
    setSubmitError(null);
    try {
      await sessionService.signIn({ email, password });
    } catch (error) {
      setSubmitError(describeError(error));
    }
  });

  const onSubmit = () => {
    void submit();
  };

  return (
    <Screen contentContainerStyle={{ justifyContent: "center" }}>
      <View style={{ gap: 4 }}>
        <AppText variant="largeTitle">Дневник состояния</AppText>
        <AppText secondary>Войдите, чтобы продолжить</AppText>
      </View>

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
            autoComplete="current-password"
            textContentType="password"
            returnKeyType="go"
            onSubmitEditing={onSubmit}
          />
        )}
      />

      {submitError ? (
        <AppText accessibilityRole="alert" variant="callout" style={{ color: theme.danger }}>
          {submitError}
        </AppText>
      ) : null}

      <Button title="Войти" onPress={onSubmit} loading={isSubmitting} />

      <Link href="/sign-up" style={{ alignSelf: "center" }}>
        <AppText style={{ color: theme.primary }}>Нет аккаунта? Зарегистрироваться</AppText>
      </Link>
    </Screen>
  );
}
