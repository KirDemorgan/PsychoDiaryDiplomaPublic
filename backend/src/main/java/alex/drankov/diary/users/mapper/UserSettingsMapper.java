package alex.drankov.diary.users.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.users.dto.UserSettingsResponse;
import alex.drankov.diary.users.entity.UserSettings;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface UserSettingsMapper {
    UserSettingsResponse response(UserSettings settings);
}
