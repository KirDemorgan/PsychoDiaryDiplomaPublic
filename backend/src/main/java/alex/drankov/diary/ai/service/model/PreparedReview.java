package alex.drankov.diary.ai.service.model;

import alex.drankov.diary.tags.entity.CandidateDecision;
import alex.drankov.diary.tags.entity.EntryTag;

import java.util.List;

public record PreparedReview(List<EntryTag> tags, List<CandidateDecision> decisions) {
}
