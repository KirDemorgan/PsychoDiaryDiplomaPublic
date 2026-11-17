package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.dto.CandidateResponse;
import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.entity.AnalysisCandidate;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.tags.dto.ManualTagRequest;
import alex.drankov.diary.tags.dto.TagDecisionRequest;
import alex.drankov.diary.tags.dto.TagReviewRequest;
import alex.drankov.diary.tags.entity.TagAction;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagReview;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;
import alex.drankov.diary.tags.repository.CandidateDecisionRepository;
import alex.drankov.diary.users.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateReviewTest {
    private final AiTaskRepository tasks = mock(AiTaskRepository.class);
    private final AnalysisCandidateRepository candidates = mock(AnalysisCandidateRepository.class);
    private final CandidateDecisionRepository decisions = mock(CandidateDecisionRepository.class);
    private final CandidateReview reviews = new CandidateReview(tasks, candidates, decisions);
    private final User owner = user();
    private final DiaryEntry entry = persisted(new DiaryEntry(owner.getId(), "тревожно", NOW, "UTC", true));
    private final TagReview review = persisted(new TagReview(entry.getId(), 1, true));
    private AnalysisCandidate candidate;
    private final LocalDate day = LocalDate.of(2026, 10, 13);

    @BeforeEach
    void setup() {
        var task = persisted(new AiTask(owner.getId(), entry.getId(), null, 1, 0, "hash", "test-adapter", AiKind.ENTRY));
        task.finish(AiStatus.SUCCEEDED, null, NOW);
        candidate = persisted(new AnalysisCandidate(task, new CandidateResponse(UUID.randomUUID(), TagCode.ANXIETY,
                "STATE", "тревожно", 0, 8, TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.SELF)));
        when(tasks.findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(owner.getId(), entry.getId(), 1)).thenReturn(Optional.of(task));
        when(candidates.findByTaskIdOrderByStartIndexAscIdAsc(task.getId())).thenReturn(List.of(candidate));
    }

    @Test
    @DisplayName("ACCEPT подтверждает дату и источник AI, EDIT сохраняет исходный кандидат неизменным")
    void acceptedAndEdited() {
        var accepted = reviews.prepare(entry, review, request(TagAction.ACCEPT, null, day, true));
        assertThat(accepted.tags().getFirst().getOrigin()).isEqualTo(TagOrigin.AI);
        assertThat(accepted.tags().getFirst().getCandidateId()).isEqualTo(candidate.getId());
        assertThat(accepted.tags().getFirst().observed()).isTrue();
        var replacement = new ManualTagRequest(TagCode.ANXIETY, "тревожно", 0, 8, TagPresence.UNCERTAIN,
                TagTemporal.CURRENT, TagSubject.SELF, null);
        var edited = reviews.prepare(entry, review, request(TagAction.EDIT, replacement, null, true));
        assertThat(edited.tags().getFirst().observed()).isFalse();
        assertThat(candidate.getPresence()).isEqualTo(TagPresence.PRESENT);
        assertThat(accepted.decisions().getFirst().getAction()).isEqualTo(TagAction.ACCEPT);
        assertThat(edited.decisions().getFirst().getAction()).isEqualTo(TagAction.EDIT);
    }

    @Test
    @DisplayName("REJECT и PENDING не становятся фактами; полное ревью требует все решения и запрещает PENDING")
    void rejectedAndPending() {
        assertThat(reviews.prepare(entry, review, request(TagAction.REJECT, null, null, true)).tags()).isEmpty();
        assertThat(reviews.prepare(entry, review, request(TagAction.PENDING, null, null, false)).tags()).isEmpty();
        assertThatThrownBy(() -> reviews.prepare(entry, review, request(TagAction.PENDING, null, null, true))).hasMessageContaining("PENDING");
        assertThatThrownBy(() -> reviews.prepare(entry, review, new TagReviewRequest(1, true, List.of(), List.of())))
                .hasMessageContaining("каждого");
        var foreign = new TagDecisionRequest(UUID.randomUUID(), TagAction.ACCEPT, null, day);
        assertThatThrownBy(() -> reviews.prepare(entry, review, new TagReviewRequest(1, true, List.of(foreign), List.of())))
                .hasMessageContaining("текущему");
    }

    private TagReviewRequest request(
            TagAction action,
            ManualTagRequest replacement,
            LocalDate date,
            boolean complete
    ) {
        return new TagReviewRequest(1, complete, List.of(new TagDecisionRequest(candidate.getId(), action, replacement, date)), List.of());
    }
}
