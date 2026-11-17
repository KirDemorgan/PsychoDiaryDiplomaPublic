package alex.drankov.diary.speech.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.speech.dto.TranscriptionResponse;
import alex.drankov.diary.speech.entity.Transcription;

import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface TranscriptionMapper {
    TranscriptionResponse response(Transcription task);
}
