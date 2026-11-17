package alex.drankov.diary.ai.dto;

import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.util.UUID;

public record CandidateResponse(
        UUID candidateId,
        TagCode labelCode,
        String category,
        String quote,
        int start,
        int end,
        TagPresence presence,
        TagTemporal temporal,
        TagSubject subject
) {
}
