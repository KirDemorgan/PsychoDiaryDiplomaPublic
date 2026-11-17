import { signInSchema, signUpSchema } from "./validation";

describe("signInSchema", () => {
  it("принимает корректные данные и обрезает пробелы в email", () => {
    const parsed = signInSchema.parse({ email: "  user@example.com ", password: "secret" });

    expect(parsed.email).toBe("user@example.com");
  });

  it.each([
    ["пустой email", { email: "", password: "x" }],
    ["некорректный email", { email: "not-email", password: "x" }],
    ["пустой пароль", { email: "user@example.com", password: "" }],
    ["слишком длинный пароль", { email: "user@example.com", password: "p".repeat(73) }],
  ])("отклоняет: %s", (_name, input) => {
    expect(signInSchema.safeParse(input).success).toBe(false);
  });
});

describe("signUpSchema", () => {
  const valid = { displayName: "Анна", email: "user@example.com", password: "p".repeat(12) };

  it("принимает корректные данные", () => {
    expect(signUpSchema.safeParse(valid).success).toBe(true);
  });

  it("отклоняет короткий пароль", () => {
    expect(signUpSchema.safeParse({ ...valid, password: "short" }).success).toBe(false);
  });

  it("считает длину пароля в байтах UTF-8", () => {
    expect(signUpSchema.safeParse({ ...valid, password: "я".repeat(37) }).success).toBe(false);
    expect(signUpSchema.safeParse({ ...valid, password: "я".repeat(36) }).success).toBe(true);
  });

  it("отклоняет слишком длинное имя", () => {
    expect(signUpSchema.safeParse({ ...valid, displayName: "a".repeat(101) }).success).toBe(false);
  });
});
