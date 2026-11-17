package alex.drankov.diary.entries.dto;

import alex.drankov.diary.ai.dto.AnalysisResponse;
import alex.drankov.diary.tags.dto.TagResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EntryResponse(
        UUID id,
        String text,
        Instant occurredAt,
        String timeZone,
        int revision,
        long version,
        boolean reportVisible,
        boolean reviewComplete,
        List<TagResponse> tags,
        AnalysisResponse latestAnalysis,
        Instant createdAt,
        Instant updatedAt
) {
    @Override
    public String toString() {
        return "EntryResponse[id=" + id + "]";
    }
}
