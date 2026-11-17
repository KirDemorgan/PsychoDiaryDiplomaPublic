package alex.drankov.diary.entries.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EntrySummaryResponse(
        UUID id,
        Instant occurredAt,
        String timeZone,
        LocalDate localDateInRequestedZone,
        String textPreview,
        int revision,
        long version,
        boolean reportVisible,
        List<String> confirmedTags,
        String analysisStatus
) {
    @Override
    public String toString() {
        return "EntrySummary[id=" + id + "]";
    }
}
