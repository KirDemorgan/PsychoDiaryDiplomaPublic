package alex.drankov.diary.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <S, T> PageResponse<T> of(Page<S> result, Function<S, T> mapping) {
        return new PageResponse<>(result.stream().map(mapping).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
