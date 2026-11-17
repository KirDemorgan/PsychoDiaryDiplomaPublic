package alex.drankov.diary.ai.dto;

import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

public record ModelCandidate(
        TagCode labelCode,
        String quote,
        Integer occurrence,
        TagPresence presence,
        TagTemporal temporal,
        TagSubject subject
) {
}
