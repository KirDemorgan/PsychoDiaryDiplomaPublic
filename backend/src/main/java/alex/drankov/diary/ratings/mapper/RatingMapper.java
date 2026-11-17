package alex.drankov.diary.ratings.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.ratings.dto.RatingResponse;
import alex.drankov.diary.ratings.entity.StateRating;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface RatingMapper {
    RatingResponse response(StateRating rating);
}
