export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radius = {
  field: 14,
  card: 22,
  pill: 999,
} as const;

export const typography = {
  largeTitle: { fontSize: 34, fontWeight: "700", lineHeight: 41 },
  title: { fontSize: 22, fontWeight: "700", lineHeight: 28 },
  headline: { fontSize: 17, fontWeight: "600", lineHeight: 22 },
  body: { fontSize: 17, fontWeight: "400", lineHeight: 22 },
  callout: { fontSize: 16, fontWeight: "400", lineHeight: 21 },
  caption: { fontSize: 13, fontWeight: "400", lineHeight: 18 },
} as const;

export type TypographyVariant = keyof typeof typography;
