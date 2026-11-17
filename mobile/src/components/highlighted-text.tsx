import { StyleSheet, Text } from "react-native";

import { buildSegments, type HighlightSpan } from "@/features/tags/segments";
import { typography } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

export interface ColoredSpan extends HighlightSpan {
  color: string;
}

export function HighlightedText({ text, spans }: { text: string; spans: readonly ColoredSpan[] }) {
  const theme = useTheme();
  const colors = new Map(spans.map((span) => [span.id, span.color]));

  return (
    <Text style={[typography.body, styles.text, { color: theme.textPrimary }]}>
      {buildSegments(text, spans).map((segment, index) =>
        segment.spanId === null ? (
          segment.text
        ) : (
          <Text
            key={`${segment.spanId}-${index}`}
            style={{ backgroundColor: `${colors.get(segment.spanId)}66`, color: theme.textPrimary }}
          >
            {segment.text}
          </Text>
        ),
      )}
    </Text>
  );
}

const styles = StyleSheet.create({
  text: { lineHeight: 26 },
});
