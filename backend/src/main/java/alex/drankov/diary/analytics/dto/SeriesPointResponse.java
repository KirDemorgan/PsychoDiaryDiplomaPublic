package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.ratings.entity.RatingSlot;

import java.time.LocalDate;

public record SeriesPointResponse(
        LocalDate date,
        RatingSlot slot,
        Integer value
) {
}
