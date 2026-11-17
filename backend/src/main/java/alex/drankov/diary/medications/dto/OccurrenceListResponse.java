package alex.drankov.diary.medications.dto;

import java.time.LocalDate;
import java.util.List;

public record OccurrenceListResponse(
        LocalDate from,
        LocalDate to,
        String timeZone,
        List<OccurrenceResponse> items
) {
}
