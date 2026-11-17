package alex.drankov.diary.reports.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SourceRefResponse(
        String type,
        UUID id,
        Long version,
        Integer revision,
        LocalDate date
) {
}
