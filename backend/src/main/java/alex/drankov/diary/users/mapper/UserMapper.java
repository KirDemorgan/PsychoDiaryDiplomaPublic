package alex.drankov.diary.users.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.users.dto.UserResponse;
import alex.drankov.diary.users.entity.User;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface UserMapper {
    UserResponse response(User user);
}
