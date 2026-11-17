import { ScrollView, type ScrollViewProps } from "react-native";

import { Backdrop } from "./backdrop";

export function BackdropScroll({ children, style, ...rest }: ScrollViewProps) {
  return (
    <ScrollView style={[{ backgroundColor: "transparent" }, style]} {...rest}>
      <Backdrop />
      {children}
    </ScrollView>
  );
}
