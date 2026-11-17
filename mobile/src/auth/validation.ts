import { z } from "zod";

const email = z
  .string()
  .trim()
  .min(1, "Введите email")
  .max(254, "Слишком длинный email")
  .pipe(z.email("Проверьте формат email"));

export const signInSchema = z.object({
  email,
  password: z.string().min(1, "Введите пароль").max(72, "Не более 72 байт"),
});

export const signUpSchema = z.object({
  displayName: z.string().trim().max(100, "Не более 100 символов"),
  email,
  password: z
    .string()
    .min(12, "Минимум 12 символов")
    .refine((value) => new TextEncoder().encode(value).length <= 72, "Пароль не должен превышать 72 байта"),
});

export type SignInForm = z.infer<typeof signInSchema>;
export type SignUpForm = z.infer<typeof signUpSchema>;
