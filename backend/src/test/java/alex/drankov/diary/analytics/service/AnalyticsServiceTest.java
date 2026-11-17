package alex.drankov.diary.analytics.service;

import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.medications.dto.OccurrenceListResponse;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.service.IntakeService;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;
import alex.drankov.diary.tags.mapper.TagMapper;
import alex.drankov.diary.tags.repository.EntryTagRepository;
import alex.drankov.diary.tags.service.TagQuery;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock
    RatingRepository ratings;
    @Mock
    EntryRepository entries;
    @Mock
    EntryTagRepository tags;
    @Mock
    MedicationRepository medications;
    @Mock
    IntakeRepository intakes;
    @Mock
    IntakeService intakeService;
    @Mock
    UserAccess access;
    @Mock
    TagQuery tagQuery;
    private final User owner = user();
    private final LocalDate day = LocalDate.of(2026, 10, 13);
    private AnalyticsService service;

    @BeforeEach
    void setup() {
        service = new AnalyticsService(ratings, entries, tags, medications, intakes, intakeService, access,
                new AnalyticsCalculator(), Mappers.getMapper(RatingMapper.class), Mappers.getMapper(EntryMapper.class),
                tagQuery, Mappers.getMapper(TagMapper.class));
        lenient().when(access.read(owner.getId())).thenReturn(owner);
        lenient().when(intakeService.occurrences(eq(owner.getId()), any(), any(), anyString(), isNull()))
                .thenAnswer(call -> new OccurrenceListResponse(call.getArgument(1), call.getArgument(2), call.getArgument(3), List.of()));
    }

    @Test
    @DisplayName("Календарь включает пустые даты, различает ноль и NULL, считает только проверенные записи")
    void calendar() {
        var rating = persisted(new StateRating(owner.getId(), day, RatingSlot.MORNING));
        rating.replace(0, null, 10, "UTC");
        when(ratings.findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(owner.getId(), day, day.plusDays(1)))
                .thenReturn(List.of(rating));
        var checked = persisted(new DiaryEntry(owner.getId(), "сон", NOW, "UTC", true));
        checked.review(UUID.randomUUID(), true);
        var unchecked = persisted(new DiaryEntry(owner.getId(), "другая запись", NOW, "UTC", true));
        when(entries.inPeriod(eq(owner.getId()), any(), any())).thenReturn(List.of(checked, unchecked));
        when(tags.currentForUser(owner.getId())).thenReturn(List.of(tag(checked, TagPresence.PRESENT), tag(checked, TagPresence.NEGATED)));
        var result = service.calendar(owner.getId(), day, day.plusDays(1), null);
        assertThat(result.days()).hasSize(2);
        var today = result.days().getFirst();
        assertThat(today.ratings().get(RatingSlot.MORNING).anxiety()).isZero();
        assertThat(today.ratings().get(RatingSlot.DAY)).isNull();
        assertThat(today.entryCount()).isEqualTo(2);
        assertThat(today.reviewedEntryCount()).isEqualTo(1);
        assertThat(today.pendingReviewCount()).isEqualTo(1);
        assertThat(today.confirmedPresentTagCodes()).containsExactly(TagCode.SLEEP_DEFICIT);
        assertThat(today.ambiguousTagCodes()).containsExactly(TagCode.SLEEP_DEFICIT);
        assertThat(result.days().get(1).entryCount()).isZero();
    }

    @Test
    @DisplayName("День выдаёт пагинацию, контекст с цитатами и принадлежность источника")
    void pagedDayAndContext() {
        var entry = persisted(new DiaryEntry(owner.getId(), "сон", NOW, "UTC", true));
        when(entries.inPeriod(eq(owner.getId()), any(), any())).thenReturn(List.of(entry));
        when(tags.currentForUser(owner.getId())).thenReturn(List.of(tag(entry, TagPresence.PRESENT)));
        when(tagQuery.observedCodes(entry)).thenReturn(List.of("SLEEP_DEFICIT"));
        var result = service.day(owner.getId(), day, "UTC", 0, 20);
        assertThat(result.entries().items().getFirst().confirmedTags()).containsExactly("SLEEP_DEFICIT");
        assertThat(result.context().getFirst().entryId()).isEqualTo(entry.getId());
        assertThat(result.context().getFirst().tag().quote()).isEqualTo("сон");
        assertThat(service.day(owner.getId(), day, "UTC", Integer.MAX_VALUE, 100).entries().items()).isEmpty();
    }

    @Test
    @DisplayName("Пустой обзор и сравнение сохраняют явную недостаточность данных и версию источников")
    void emptyOverviewAndComparison() {
        assertThat(service.overview(owner.getId(), day, day, null, 7).dailyValues().getFirst().anxietyMean()).isNull();
        assertThat(service.comparison(owner.getId(), day, day, null, TagCode.ANXIETY, Metric.MOOD).status())
                .isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("Неверные периоды, зоны, пороги и отсутствие метрики отклоняются")
    void invalidQuery() {
        assertThatThrownBy(() -> service.overview(owner.getId(), day, null, "UTC", 7)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.overview(owner.getId(), day, day, "wrong", 7)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.overview(owner.getId(), day, day, "UTC", 11)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.comparison(owner.getId(), day, day, "UTC", null, Metric.MOOD)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.comparison(owner.getId(), day, day, "UTC", TagCode.ANXIETY, null)).isInstanceOf(ApiException.class);
    }

    private EntryTag tag(
            DiaryEntry entry,
            TagPresence presence
    ) {
        return new EntryTag(UUID.randomUUID(), entry.getId(), 1, TagCode.SLEEP_DEFICIT, "сон", 0, 3, presence,
                TagTemporal.CURRENT, TagSubject.SELF, day, TagOrigin.MANUAL);
    }
}
