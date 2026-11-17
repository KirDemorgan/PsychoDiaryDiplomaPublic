package alex.drankov.diary.tags.service;

import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.ai.service.AnalysisQuery;
import alex.drankov.diary.ai.service.CandidateReview;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.tags.dto.ManualTagRequest;
import alex.drankov.diary.tags.dto.TagDecisionRequest;
import alex.drankov.diary.tags.dto.TagReviewRequest;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagAction;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagReview;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;
import alex.drankov.diary.tags.mapper.TagMapper;
import alex.drankov.diary.tags.repository.CandidateDecisionRepository;
import alex.drankov.diary.tags.repository.EntryTagRepository;
import alex.drankov.diary.tags.repository.TagReviewRepository;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {
    @Mock
    EntryRepository entries;
    @Mock
    TagReviewRepository reviews;
    @Mock
    EntryTagRepository tags;
    @Mock
    UserAccess access;
    @Mock
    Idempotency idempotency;
    private final User owner = user();
    private final DiaryEntry entry = persisted(new DiaryEntry(owner.getId(), "😀 сегодня тревожно", NOW, "UTC", true));
    private TagService service;
    private TagQuery query;

    @BeforeEach
    void setup() {
        query = new TagQuery(tags, Mappers.getMapper(TagMapper.class));
        service = new TagService(entries, reviews, tags, Mappers.getMapper(EntryMapper.class), query, access, idempotency,
                new CandidateReview(Mockito.mock(AiTaskRepository.class),
                Mockito.mock(AnalysisCandidateRepository.class),
                Mockito.mock(CandidateDecisionRepository.class)),
                Mockito.mock(AnalysisQuery.class));
        lenient().when(access.lock(owner.getId())).thenReturn(owner);
        lenient().when(entries.findByIdAndUserId(entry.getId(), owner.getId())).thenReturn(Optional.of(entry));
        lenient().when(idempotency.execute(any(), anyString(), any(), any(), eq(EntryResponse.class), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
        lenient().when(reviews.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
    }

    @Test
    @DisplayName("Ревью подтверждает цитаты текущей редакции, сохраняет происхождение и повышает версию данных")
    void reviewAndHistory() {
        var manual = tag("тревожно", 11, 19);
        assertThat(query.current(entry)).isEmpty();
        assertThat(query.observedCodes(entry)).isEmpty();
        service.review(owner.getId(), entry.getId(), "\"v0\"", UUID.randomUUID(),
                new TagReviewRequest(1, true, List.of(), List.of(manual)));
        assertThat(entry.isReviewComplete()).isTrue();
        assertThat(owner.getDataRevision()).isEqualTo(1);
        var captured = ArgumentCaptor.forClass(List.class);
        verify(tags).saveAllAndFlush(captured.capture());
        var saved = (EntryTag) captured.getValue().getFirst();
        assertThat(saved.getQuote()).isEqualTo("тревожно");
        assertThat(saved.getOrigin()).isEqualTo(TagOrigin.MANUAL);
        when(tags.findByReviewIdOrderByStartAscIdAsc(entry.getCurrentReviewId())).thenReturn(List.of(saved));
        assertThat(query.current(entry).getFirst().category()).isEqualTo("STATE");
        assertThat(query.observedCodes(entry)).containsExactly("ANXIETY");
        UUID previousReview = entry.getCurrentReviewId();
        service.review(owner.getId(), entry.getId(), "\"v0\"", UUID.randomUUID(),
                new TagReviewRequest(1, true, List.of(), List.of()));
        assertThat(entry.getCurrentReviewId()).isNotEqualTo(previousReview);
        assertThat(entry.isReviewComplete()).isTrue();
        entry.revise("другой текст", NOW, "UTC");
        assertThat(entry.getCurrentReviewId()).isNull();
        assertThat(query.current(entry)).isEmpty();
        assertThat(entry.isReviewComplete()).isFalse();
        verify(reviews, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Отрицание, будущее, чужое событие и неподтверждённая дата не становятся наблюдением")
    void context() {
        var review = persisted(new TagReview(entry.getId(), 1, false));
        entry.review(review.getId(), false);
        for (var values : List.of(
                List.of(TagPresence.NEGATED, TagTemporal.CURRENT, TagSubject.SELF),
                List.of(TagPresence.UNCERTAIN, TagTemporal.CURRENT, TagSubject.SELF),
                List.of(TagPresence.PRESENT, TagTemporal.FUTURE, TagSubject.SELF),
                List.of(TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.OTHER))) {
            var tagged = new EntryTag(review.getId(), entry.getId(), 1, TagCode.ANXIETY, "тревожно", 11, 19,
                    (TagPresence) values.get(0), (TagTemporal) values.get(1), (TagSubject) values.get(2),
                    LocalDate.of(2026, 10, 13), TagOrigin.MANUAL);
            when(tags.findByReviewIdOrderByStartAscIdAsc(review.getId())).thenReturn(List.of(tagged));
            assertThat(query.observedCodes(entry)).isEmpty();
        }
        var noDate = new EntryTag(review.getId(), entry.getId(), 1, TagCode.ANXIETY, "тревожно", 11, 19,
                TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.SELF, null, TagOrigin.MANUAL);
        assertThat(noDate.observed()).isFalse();
    }

    @Test
    @DisplayName("Точная цитата проверяется в UTF-16 и не может разрезать emoji")
    void unicodeQuotes() {
        TagService.validate(entry.getText(), tag("😀", 0, 2));
        for (var invalid : Arrays.asList(null, tag("тревожно", 0, 8), tag("выдумка", 11, 19), tag("😀", 0, 1),
                tag("\uDE00", 1, 2), tag("тревожно", -1, 19), tag("тревожно", 11, 20), tag("", 11, 11),
                new ManualTagRequest(null, "тревожно", 11, 19, TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.SELF, null))) {
            assertThatThrownBy(() -> TagService.validate(entry.getText(), invalid)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("Чужой владелец, устаревшая редакция и версия не могут подтвердить теги")
    void concurrencyAndOwnership() {
        assertThatThrownBy(() -> service.review(owner.getId(), entry.getId(), "\"v1\"", UUID.randomUUID(),
                new TagReviewRequest(1, true, List.of(), List.of()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.review(owner.getId(), entry.getId(), "\"v0\"", UUID.randomUUID(),
                new TagReviewRequest(2, true, List.of(), List.of()))).isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("STALE_REVISION"));
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), entry.getId(), "\"v0\"", UUID.randomUUID(),
                new TagReviewRequest(1, true, List.of(), List.of()))).isInstanceOf(ApiException.class);
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Дубли, отсутствующие поля и решения несуществующего AI-анализа отклоняются")
    void invalidReview() {
        for (var request : List.of(new TagReviewRequest(1, null, List.of(), List.of()),
                new TagReviewRequest(1, true, null, List.of()), new TagReviewRequest(1, true, List.of(), null),
                new TagReviewRequest(1, true, List.of(new TagDecisionRequest(UUID.randomUUID(), TagAction.ACCEPT, null, null)), List.of()),
                new TagReviewRequest(1, true, List.of(), Collections.nCopies(101, tag("тревожно", 11, 19))),
                new TagReviewRequest(1, true, List.of(), List.of(tag("тревожно", 11, 19), tag("тревожно", 11, 19))))) {
            assertThatThrownBy(() -> service.review(owner.getId(), entry.getId(), "\"v0\"", UUID.randomUUID(), request))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(service.definitions().items()).hasSize(12);
        assertThat(service.definitions().version()).isEqualTo("1");
        verify(reviews, never()).saveAndFlush(any());
    }

    private ManualTagRequest tag(
            String quote,
            int start,
            int end
    ) {
        return new ManualTagRequest(TagCode.ANXIETY, quote, start, end, TagPresence.PRESENT, TagTemporal.CURRENT,
                TagSubject.SELF, LocalDate.of(2026, 10, 13));
    }
}
