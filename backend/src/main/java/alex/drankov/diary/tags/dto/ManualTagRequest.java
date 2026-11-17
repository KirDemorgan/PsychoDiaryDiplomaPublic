package alex.drankov.diary.tags.dto;

import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.time.LocalDate;
import java.util.UUID;

public record ManualTagRequest(
        TagCode labelCode,
        String quote,
        Integer start,
        Integer end,
        TagPresence presence,
        TagTemporal temporal,
        TagSubject subject,
        LocalDate appliesToLocalDate
) {
    @Override
    public String toString() {
        return "ManualTagRequest[labelCode=" + labelCode + "]";
    }
}
