import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { AccessibilityInfo, Image, Pressable, StyleSheet, useWindowDimensions, View, type NativeScrollEvent, type NativeSyntheticEvent } from "react-native";
import Animated, {
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useSharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useSessionStore } from "@/auth/session-store";
import { AppText } from "@/components/app-text";
import { Backdrop } from "@/components/backdrop";
import { Button } from "@/components/button";
import { ONBOARDING_PAGES } from "@/features/onboarding/pages";
import { useOnboardingStore } from "@/features/onboarding/onboarding-store";
import { useReduceMotion } from "@/hooks/use-reduce-motion";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

import { OnboardingPage } from "./onboarding-page";
import { PageIndicator } from "./page-indicator";

const LOGO = require("../../../assets/images/splash-icon.png");

export function OnboardingScreen() {
  const theme = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduceMotion = useReduceMotion();
  const signedIn = useSessionStore((state) => state.status === "signedIn");
  const complete = useOnboardingStore((state) => state.complete);

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const scrollX = useSharedValue(0);
  const [page, setPage] = useState(0);
  const count = ONBOARDING_PAGES.length;
  const last = page === count - 1;

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollX.set(event.contentOffset.x);
  });

  useAnimatedReaction(
    () => Math.round(scrollX.get() / width),
    (current, previous) => {
      if (previous !== null && current !== previous) {
        scheduleOnRN(setPage, current);
      }
    },
  );

  useEffect(() => {
    void AccessibilityInfo.announceForAccessibility(`Страница ${page + 1} из ${count}. ${ONBOARDING_PAGES[page].title}`);
  }, [page, count]);

  const finish = useCallback(() => {
    void complete();
    if (signedIn) {
      router.back();
    } else {
      router.replace("/sign-in");
    }
  }, [complete, router, signedIn]);

  const next = () => {
    if (last) {
      finish();
      return;
    }
    setPage(page + 1);
    scrollRef.current?.scrollTo({ x: (page + 1) * width, animated: true });
  };

  const settle = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    setPage(Math.round(event.nativeEvent.contentOffset.x / width));
  };

  return (
    <View style={[styles.root, { backgroundColor: theme.background, paddingTop: insets.top, paddingBottom: Math.max(insets.bottom, spacing.lg) }]}>
      <Backdrop />

      <View style={styles.top}>
        <View style={styles.brand}>
          <Image source={LOGO} style={styles.logo} accessibilityIgnoresInvertColors />
          <AppText variant="headline">Дневник</AppText>
        </View>
        {last ? null : (
          <Pressable accessibilityRole="button" accessibilityLabel="Пропустить знакомство" hitSlop={12} onPress={finish}>
            <AppText style={{ color: theme.primary, fontWeight: "600" }}>Пропустить</AppText>
          </Pressable>
        )}
      </View>

      <Animated.ScrollView
        ref={scrollRef}
        testID="onboarding-pager"
        horizontal
        pagingEnabled
        bounces={false}
        showsHorizontalScrollIndicator={false}
        scrollEventThrottle={16}
        onScroll={onScroll}
        onMomentumScrollEnd={settle}
        style={styles.pager}
      >
        {ONBOARDING_PAGES.map((content, index) => (
          <OnboardingPage
            key={content.id}
            content={content}
            index={index}
            count={count}
            width={width}
            scrollX={scrollX}
            active={index === page}
            reduceMotion={reduceMotion}
          />
        ))}
      </Animated.ScrollView>

      <View style={styles.bottom}>
        <PageIndicator count={count} page={page} width={width} scrollX={scrollX} />
        <Button title={last ? "Начать" : "Далее"} onPress={next} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: "hidden" },
  top: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: spacing.xl, minHeight: 48 },
  brand: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  logo: { width: 30, height: 30 },
  pager: { flex: 1 },
  bottom: { gap: spacing.xl, paddingHorizontal: spacing.xl, paddingTop: spacing.md },
});
