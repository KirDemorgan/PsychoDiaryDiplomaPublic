package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.ratings.dto.RatingResponse;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.tags.entity.TagCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CalendarDayResponse(
        LocalDate date,
        Map<RatingSlot, RatingResponse> ratings,
        long entryCount,
        long reviewedEntryCount,
        long pendingReviewCount,
        List<TagCode> confirmedPresentTagCodes,
        List<TagCode> ambiguousTagCodes,
        MedicationDayResponse medication
) {
}
