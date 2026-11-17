package alex.drankov.diary.speech.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.speech.dto.SpeechConsentResponse;
import alex.drankov.diary.users.entity.UserSettings;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface SpeechConsentMapper {
    @Mapping(target = "enabled", source = "settings.speechEnabled")
    @Mapping(target = "consentVersion", source = "settings.speechConsentVersion")
    @Mapping(target = "acceptedAt", source = "settings.speechConsentAcceptedAt")
    SpeechConsentResponse response(UserSettings settings, String currentVersion);
}
