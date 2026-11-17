import { createContext, useContext, useEffect, type ReactNode } from "react";
import { type StyleProp, type ViewStyle } from "react-native";
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";

interface PageMotion {
  active: boolean;
  reduceMotion: boolean;
}

const PageMotionContext = createContext<PageMotion>({ active: true, reduceMotion: true });

export function PageMotionProvider({ active, reduceMotion, children }: PageMotion & { children: ReactNode }) {
  return <PageMotionContext.Provider value={{ active, reduceMotion }}>{children}</PageMotionContext.Provider>;
}

export function useEntrance(delay = 0, duration = 560): SharedValue<number> {
  const { active, reduceMotion } = useContext(PageMotionContext);
  const progress = useSharedValue(0);

  useEffect(() => {
    if (!active) {
      progress.set(0);
      return;
    }
    progress.set(
      reduceMotion ? 1 : withDelay(delay, withTiming(1, { duration, easing: Easing.out(Easing.cubic) })),
    );
  }, [active, reduceMotion, delay, duration, progress]);

  return progress;
}

export function usePop(delay = 0): SharedValue<number> {
  const { active, reduceMotion } = useContext(PageMotionContext);
  const progress = useSharedValue(0);

  useEffect(() => {
    if (!active) {
      progress.set(0);
      return;
    }
    progress.set(reduceMotion ? 1 : withDelay(delay, withSpring(1, { damping: 11, stiffness: 160 })));
  }, [active, reduceMotion, delay, progress]);

  return progress;
}

interface RevealProps {
  delay?: number;
  distance?: number;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}

export function Reveal({ delay = 0, distance = 16, style, children }: RevealProps) {
  const progress = useEntrance(delay);
  const animated = useAnimatedStyle(() => ({
    opacity: progress.get(),
    transform: [{ translateY: (1 - progress.get()) * distance }],
  }));

  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

interface PopProps {
  delay?: number;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}

export function Pop({ delay = 0, style, children }: PopProps) {
  const progress = usePop(delay);
  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, progress.get() * 2),
    transform: [{ scale: 0.6 + 0.4 * progress.get() }],
  }));

  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

interface GrowBarProps {
  value: number;
  color: string;
  trackColor: string;
  delay?: number;
}

export function GrowBar({ value, color, trackColor, delay = 0 }: GrowBarProps) {
  const progress = useEntrance(delay, 760);
  const fill = useAnimatedStyle(() => ({ width: `${Math.round(value * 100 * progress.get())}%` }));

  return (
    <Animated.View style={{ height: 10, borderRadius: 5, backgroundColor: trackColor, overflow: "hidden" }}>
      <Animated.View style={[{ height: 10, borderRadius: 5, backgroundColor: color }, fill]} />
    </Animated.View>
  );
}

interface WipeProps {
  width: number;
  height: number;
  delay?: number;
  children: ReactNode;
}

export function Wipe({ width, height, delay = 0, children }: WipeProps) {
  const progress = useEntrance(delay, 1100);
  const reveal = useAnimatedStyle(() => ({ width: width * progress.get() }));

  return (
    <Animated.View style={[{ height, overflow: "hidden" }, reveal]}>
      <Animated.View style={{ width, height }}>{children}</Animated.View>
    </Animated.View>
  );
}
