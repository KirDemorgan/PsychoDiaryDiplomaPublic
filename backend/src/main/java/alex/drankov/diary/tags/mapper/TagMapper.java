package alex.drankov.diary.tags.mapper;

import alex.drankov.diary.common.mapping.MappingConfig;
import alex.drankov.diary.tags.dto.TagResponse;
import alex.drankov.diary.tags.entity.EntryTag;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MappingConfig.class)
public interface TagMapper {
    @Mapping(target = "category", expression = "java(tag.getLabelCode().getCategory())")
    TagResponse response(EntryTag tag);
}
