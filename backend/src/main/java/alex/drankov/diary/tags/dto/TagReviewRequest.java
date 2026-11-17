package alex.drankov.diary.tags.dto;

import java.util.List;

public record TagReviewRequest(
        Integer revision,
        Boolean complete,
        List<TagDecisionRequest> decisions,
        List<ManualTagRequest> manualTags
) {
    @Override
    public String toString() {
        return "TagReviewRequest[revision=" + revision + "]";
    }
}
