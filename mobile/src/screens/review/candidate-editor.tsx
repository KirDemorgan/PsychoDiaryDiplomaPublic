import { useState } from "react";
import { StyleSheet, View } from "react-native";

import type { Candidate } from "@/api/tags-types";
import { AppText } from "@/components/app-text";
import { Button } from "@/components/button";
import { Chip } from "@/components/chip";
import { SegmentedPicker } from "@/components/segmented-picker";
import { PRESENCE_OPTIONS, TEMPORAL_OPTIONS } from "@/features/tags/labels";
import type { CandidateEdit } from "@/features/tags/review-state";
import { TAG_CODES, TAG_LABELS, tagColor } from "@/features/tags/tag-colors";
import { spacing } from "@/theme/tokens";
import { useTheme } from "@/theme/use-theme";

interface CandidateEditorProps {
  candidate: Candidate;
  initial: CandidateEdit;
  onSave: (edit: CandidateEdit) => void;
  onCancel: () => void;
}

export function CandidateEditor({ candidate, initial, onSave, onCancel }: CandidateEditorProps) {
  const theme = useTheme();
  const [edit, setEdit] = useState(initial);

  return (
    <View style={styles.container}>
      <AppText variant="caption" secondary>
        «{candidate.quote}»
      </AppText>

      <View style={styles.chips} accessibilityRole="radiogroup" accessibilityLabel="Тег">
        {TAG_CODES.map((code) => (
          <Chip
            key={code}
            label={TAG_LABELS[code]}
            color={tagColor(code, theme)}
            selected={edit.labelCode === code}
            onPress={() => setEdit({ ...edit, labelCode: code })}
          />
        ))}
      </View>

      <AppText variant="caption" secondary>
        Наличие
      </AppText>
      <SegmentedPicker
        accessibilityLabel="Наличие"
        options={PRESENCE_OPTIONS}
        value={edit.presence}
        onChange={(presence) => setEdit({ ...edit, presence })}
      />

      <AppText variant="caption" secondary>
        Когда
      </AppText>
      <SegmentedPicker
        accessibilityLabel="Время"
        options={TEMPORAL_OPTIONS}
        value={edit.temporal}
        onChange={(temporal) => setEdit({ ...edit, temporal })}
      />

      <View style={styles.actions}>
        <Button title="Отмена" variant="secondary" onPress={onCancel} />
        <Button title="Готово" onPress={() => onSave(edit)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.md },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  actions: { gap: spacing.sm },
});
