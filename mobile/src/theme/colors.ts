export interface Palette {
  background: string;
  surface: string;
  surfaceMuted: string;
  border: string;
  textPrimary: string;
  textSecondary: string;
  primary: string;
  onPrimary: string;
  danger: string;
  tagSleep: string;
  tagAnxiety: string;
  tagCalm: string;
  tagContext: string;
  tagSymptom: string;
  tagState: string;
  anxiety: string;
  mood: string;
  energy: string;
}

export const lightPalette: Palette = {
  background: "#F2F6FB",
  surface: "#FFFFFF",
  surfaceMuted: "#E8EEF5",
  border: "#D9E1EA",
  textPrimary: "#111B26",
  textSecondary: "#5B6B7C",
  primary: "#12908C",
  onPrimary: "#FFFFFF",
  danger: "#C62F3E",
  tagSleep: "#B79CF2",
  tagAnxiety: "#F4A56B",
  tagCalm: "#7EDCC3",
  tagContext: "#8FB8F5",
  tagSymptom: "#F59AA8",
  tagState: "#E8CB62",
  anxiety: "#8B6CF0",
  mood: "#18B39F",
  energy: "#3B82F6",
};

export const darkPalette: Palette = {
  background: "#0D141B",
  surface: "#16212B",
  surfaceMuted: "#1F2D39",
  border: "#2A3948",
  textPrimary: "#EEF3F8",
  textSecondary: "#9AABBB",
  primary: "#2BC4BE",
  onPrimary: "#04201F",
  danger: "#FF6B78",
  tagSleep: "#B79CF2",
  tagAnxiety: "#F4A56B",
  tagCalm: "#7EDCC3",
  tagContext: "#8FB8F5",
  tagSymptom: "#F59AA8",
  tagState: "#E8CB62",
  anxiety: "#A88DFF",
  mood: "#34D1BD",
  energy: "#6BA3FF",
};
