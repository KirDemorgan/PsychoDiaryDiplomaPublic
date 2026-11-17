package alex.drankov.diary.tags.dto;

import alex.drankov.diary.tags.entity.TagAction;

import java.time.LocalDate;
import java.util.UUID;

public record TagDecisionRequest(
        UUID candidateId,
        TagAction action,
        ManualTagRequest replacement,
        LocalDate appliesToLocalDate
) {
}
