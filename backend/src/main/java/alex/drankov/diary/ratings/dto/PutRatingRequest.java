package alex.drankov.diary.ratings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PutRatingRequest(
        @JsonProperty(required = true)
        @Min(0)
        @Max(10)
        Integer anxiety,
        @JsonProperty(required = true)
        @Min(0)
        @Max(10)
        Integer mood,
        @JsonProperty(required = true)
        @Min(0)
        @Max(10)
        Integer energy,
        @NotBlank
        String timeZone
) {
}
