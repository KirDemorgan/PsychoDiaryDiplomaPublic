package alex.drankov.diary.entries.mapper;

import alex.drankov.diary.ai.dto.AnalysisResponse;
import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.dto.EntrySummaryResponse;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.tags.dto.TagResponse;

import java.time.ZoneId;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface EntryMapper {
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "latestAnalysis", source = "analysis")
    @Mapping(target = "id", source = "entry.id")
    @Mapping(target = "revision", source = "entry.revision")
    @Mapping(target = "createdAt", source = "entry.createdAt")
    EntryResponse response(
            DiaryEntry entry,
            List<TagResponse> tags,
            AnalysisResponse analysis
    );

    default EntryResponse response(
            DiaryEntry entry,
            List<TagResponse> tags
    ) {
        return response(entry, tags, null);
    }

    default EntryResponse response(DiaryEntry entry) {
        return response(entry, List.of());
    }

    default EntrySummaryResponse summary(
            DiaryEntry entry,
            ZoneId requestedZone
    ) {
        return summary(entry, requestedZone, List.of());
    }

    default EntrySummaryResponse summary(
            DiaryEntry entry,
            ZoneId requestedZone,
            List<String> confirmedTags
    ) {
        return summary(entry, requestedZone, confirmedTags, null);
    }

    default EntrySummaryResponse summary(
            DiaryEntry entry,
            ZoneId requestedZone,
            List<String> confirmedTags,
            String analysisStatus
    ) {
        var text = entry.getText();
        int end = text.offsetByCodePoints(0, Math.min(160, text.codePointCount(0, text.length())));
        return new EntrySummaryResponse(entry.getId(), entry.getOccurredAt(), entry.getTimeZone(),
                entry.getOccurredAt().atZone(requestedZone).toLocalDate(), text.substring(0, end),
                entry.getRevision(), entry.getVersion(), entry.isReportVisible(), confirmedTags, analysisStatus);
    }
}
