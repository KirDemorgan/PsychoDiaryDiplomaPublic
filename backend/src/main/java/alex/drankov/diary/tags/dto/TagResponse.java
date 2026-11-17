package alex.drankov.diary.tags.dto;

import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.time.LocalDate;
import java.util.UUID;

public record TagResponse(
        UUID id,
        TagCode labelCode,
        String category,
        String quote,
        int start,
        int end,
        TagPresence presence,
        TagTemporal temporal,
        TagSubject subject,
        LocalDate appliesToLocalDate,
        TagOrigin origin
) {
    @Override
    public String toString() {
        return "TagResponse[id=" + id + "]";
    }
}
