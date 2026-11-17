package alex.drankov.diary.ratings.dto;

import alex.drankov.diary.ratings.entity.RatingSlot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RatingResponse(
        UUID id,
        LocalDate localDate,
        RatingSlot slot,
        String timeZone,
        Integer anxiety,
        Integer mood,
        Integer energy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
