package alex.drankov.diary.analytics.dto;



public record ComparisonExcludedResponse(
        int noRating,
        int noEntry,
        int incompleteContext,
        int ambiguousContext
) {
}
