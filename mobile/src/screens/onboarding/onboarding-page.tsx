import { StyleSheet, View } from "react-native";
import Animated, { Extrapolation, interpolate, useAnimatedStyle, type SharedValue } from "react-native-reanimated";

import { AppText } from "@/components/app-text";
import type { OnboardingPageContent, OnboardingPageId } from "@/features/onboarding/pages";
import { spacing } from "@/theme/tokens";

import { PageMotionProvider } from "./page-motion";
import { MedicationsMockup } from "./mockups/medications-mockup";
import { RatingsMockup } from "./mockups/ratings-mockup";
import { ReportMockup } from "./mockups/report-mockup";
import { TagsMockup } from "./mockups/tags-mockup";

const MOCKUPS: Record<OnboardingPageId, (props: { width: number }) => React.JSX.Element> = {
  ratings: RatingsMockup,
  tags: TagsMockup,
  medications: MedicationsMockup,
  report: ReportMockup,
};

const MOCKUP_MAX_WIDTH = 360;

interface OnboardingPageProps {
  content: OnboardingPageContent;
  index: number;
  count: number;
  width: number;
  scrollX: SharedValue<number>;
  active: boolean;
  reduceMotion: boolean;
}

export function OnboardingPage({ content, index, count, width, scrollX, active, reduceMotion }: OnboardingPageProps) {
  const Mockup = MOCKUPS[content.id];
  const mockupWidth = Math.min(width - spacing.xl * 2, MOCKUP_MAX_WIDTH);

  const stageStyle = useAnimatedStyle(() => {
    const progress = (scrollX.get() - index * width) / width;
    return {
      opacity: interpolate(progress, [-1, 0, 1], [0, 1, 0], Extrapolation.CLAMP),
      transform: [
        { translateX: progress * width * 0.28 },
        { scale: interpolate(progress, [-1, 0, 1], [0.92, 1, 0.92], Extrapolation.CLAMP) },
      ],
    };
  });

  const textStyle = useAnimatedStyle(() => {
    const progress = (scrollX.get() - index * width) / width;
    return {
      opacity: interpolate(progress, [-0.8, 0, 0.8], [0, 1, 0], Extrapolation.CLAMP),
      transform: [{ translateX: progress * width * 0.12 }],
    };
  });

  return (
    <View
      style={[styles.page, { width }]}
      accessibilityLabel={`Страница ${index + 1} из ${count}`}
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? "auto" : "no-hide-descendants"}
    >
      <PageMotionProvider active={active} reduceMotion={reduceMotion}>
        <Animated.View style={[styles.stage, stageStyle]}>
          <Mockup width={mockupWidth} />
        </Animated.View>
      </PageMotionProvider>
      <Animated.View style={[styles.text, textStyle]}>
        <AppText variant="largeTitle" accessibilityRole="header" style={styles.title}>
          {content.title}
        </AppText>
        <AppText secondary style={styles.body}>
          {content.body}
        </AppText>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, paddingHorizontal: spacing.xl },
  stage: { flex: 1, alignItems: "center", justifyContent: "center" },
  text: { gap: spacing.md, paddingBottom: spacing.lg },
  title: { fontSize: 30, lineHeight: 36 },
  body: { fontSize: 17, lineHeight: 24 },
});
