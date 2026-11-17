package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.ai.service.model.PreparedReview;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.tags.dto.ManualTagRequest;
import alex.drankov.diary.tags.dto.TagReviewRequest;
import alex.drankov.diary.tags.entity.CandidateDecision;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagAction;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagReview;
import alex.drankov.diary.tags.repository.CandidateDecisionRepository;
import alex.drankov.diary.tags.service.TagService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateReview {
    private final AiTaskRepository tasks;
    private final AnalysisCandidateRepository candidates;
    private final CandidateDecisionRepository decisions;

    public PreparedReview prepare(
            DiaryEntry entry,
            TagReview review,
            TagReviewRequest request
    ) {
        var current = tasks.findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(entry.getUserId(), entry.getId(), entry.getRevision())
                .filter(task -> task.getStatus() == AiStatus.SUCCEEDED);
        var allowed = current.map(task -> candidates.findByTaskIdOrderByStartIndexAscIdAsc(task.getId())).orElse(List.of())
                .stream().collect(Collectors.toMap(value -> value.getId(), Function.identity()));
        if (request.decisions().size() > 100 || request.decisions().stream().anyMatch(value -> value == null
                || value.candidateId() == null || value.action() == null || !allowed.containsKey(value.candidateId()))) {
            throw new ApiException(409, "ANALYSIS_NOT_AVAILABLE", "Решение не относится к текущему анализу");
        }
        var selected = request.decisions().stream().map(value -> value.candidateId()).collect(Collectors.toSet());
        if (selected.size() != request.decisions().size() || !selected.equals(allowed.keySet())) {
            throw ApiException.invalid("Передайте решение для каждого текущего кандидата без дублей");
        }
        var result = new ArrayList<EntryTag>();
        var history = new ArrayList<CandidateDecision>();
        for (var decision : request.decisions()) {
            if (decision.action() == TagAction.PENDING && request.complete()) {
                throw ApiException.invalid("Завершённое ревью не содержит PENDING");
            }
            if (decision.action() != TagAction.EDIT && decision.replacement() != null
                    || decision.action() != TagAction.ACCEPT && decision.appliesToLocalDate() != null) {
                throw ApiException.invalid("Исправление передаётся только для EDIT, дата решения — для ACCEPT");
            }
            var candidate = allowed.get(decision.candidateId());
            var tag = decision.action() == TagAction.EDIT ? decision.replacement()
                    : new ManualTagRequest(candidate.getLabelCode(), candidate.getQuote(), candidate.getStartIndex(), candidate.getEndIndex(),
                    candidate.getPresence(), candidate.getTemporal(), candidate.getSubject(), decision.appliesToLocalDate());
            if (decision.action() == TagAction.ACCEPT || decision.action() == TagAction.EDIT) {
                TagService.validate(entry.getText(), tag);
                var confirmed = tag(entry, review, tag, TagOrigin.AI);
                confirmed.sourceCandidate(candidate.getId());
                result.add(confirmed);
            }
            history.add(new CandidateDecision(review, candidate.getId(), decision.action(),
                    tag == null ? null : tag.appliesToLocalDate()));
        }
        for (var manual : request.manualTags()) {
            TagService.validate(entry.getText(), manual);
            result.add(tag(entry, review, manual, TagOrigin.MANUAL));
        }
        var unique = new HashSet<String>();
        for (var tag : result) {
            if (!unique.add(tag.getLabelCode() + ":" + tag.getStart() + ":" + tag.getEnd())) {
                throw ApiException.invalid("Тег одного фрагмента не должен повторяться");
            }
        }
        return new PreparedReview(List.copyOf(result), List.copyOf(history));
    }

    public void saveDecisions(PreparedReview review) {
        decisions.saveAll(review.decisions());
    }

    private EntryTag tag(
            DiaryEntry entry,
            TagReview review,
            ManualTagRequest tag,
            TagOrigin origin
    ) {
        return new EntryTag(review.getId(), entry.getId(), entry.getRevision(), tag.labelCode(), tag.quote(), tag.start(), tag.end(),
                tag.presence(), tag.temporal(), tag.subject(), tag.appliesToLocalDate(), origin);
    }
}
