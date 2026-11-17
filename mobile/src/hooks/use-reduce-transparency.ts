import { useEffect, useState } from "react";
import { AccessibilityInfo } from "react-native";

export function useReduceTransparency(): boolean {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    let active = true;
    void AccessibilityInfo.isReduceTransparencyEnabled().then((value) => {
      if (active) {
        setReduced(value);
      }
    });
    const subscription = AccessibilityInfo.addEventListener("reduceTransparencyChanged", setReduced);
    return () => {
      active = false;
      subscription.remove();
    };
  }, []);

  return reduced;
}
