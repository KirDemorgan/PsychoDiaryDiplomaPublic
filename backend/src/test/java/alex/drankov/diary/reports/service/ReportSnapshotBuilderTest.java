package alex.drankov.diary.reports.service;

import alex.drankov.diary.analytics.service.AnalyticsCalculator;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.service.model.ReportSourceData;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;
import alex.drankov.diary.tags.mapper.TagMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportSnapshotBuilderTest {
    @Test
    @DisplayName("Пакет содержит вычисленные факты и точные цитаты; ID воспроизводимы, обзор явно шаблонный")
    void factsAndProvenance() {
        var owner = user();
        var day = LocalDate.of(2026, 10, 13);
        var request = request(day, Set.of(ReportSection.RATINGS, ReportSection.TAGS, ReportSection.NOTES,
                ReportSection.AI_OVERVIEW));
        var rating = persisted(new StateRating(owner.getId(), day, RatingSlot.MORNING));
        rating.replace(8, 0, null, "UTC");
        var entry = persisted(new DiaryEntry(owner.getId(), "Сегодня тревожно", NOW, "UTC", true));
        entry.review(UUID.randomUUID(), true);
        var tag = persisted(new EntryTag(entry.getCurrentReviewId(), entry.getId(), 1, TagCode.ANXIETY,
                "тревожно", 8, 16, TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.SELF, day, TagOrigin.MANUAL));
        var source = new ReportSourceData(0, request, List.of(rating), List.of(entry), List.of(tag),
                List.of(), List.of(), List.of(), List.of());
        var builder = builder(source);
        var result = builder.build(owner, request);
        var repeated = builder.build(owner, request);
        assertThat(result.facts()).isEqualTo(repeated.facts());
        assertThat(result.overviewMode()).isEqualTo(OverviewMode.TEMPLATE);
        assertThat(result.overview().provenance().modelId()).isNull();
        assertThat(result.facts()).anySatisfy(fact -> {
            assertThat(fact.value().quote()).isEqualTo("тревожно");
            assertThat(fact.sourceRefs()).anySatisfy(ref -> {
                assertThat(ref.id()).isEqualTo(entry.getId());
                assertThat(ref.revision()).isEqualTo(1);
            });
        });
        assertThat(result.previewData().comparisons()).hasSize(3);
        assertThat(result.overview().claims()).allSatisfy(claim ->
                assertThat(result.facts().stream().map(fact -> fact.factId()).toList()).containsAll(claim.factIds()));
        assertThat(result.overview().claims()).anySatisfy(claim -> assertThat(claim.text()).contains("нет оценок"));
        assertThat(result.overview().claims()).anySatisfy(claim -> assertThat(claim.text()).contains("недостаточно"));
    }

    @Test
    @DisplayName("Снятые разделы не возвращают записи, теги или обзор; пустые оценки остаются неизвестными")
    void excludedSections() {
        var owner = user();
        var day = LocalDate.of(2026, 10, 13);
        var request = request(day, Set.of(ReportSection.RATINGS));
        var source = new ReportSourceData(0, request, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        var result = builder(source).build(owner, request);
        assertThat(result.overview()).isNull();
        assertThat(result.overviewMode()).isEqualTo(OverviewMode.NONE);
        assertThat(result.previewData().entries()).isEmpty();
        assertThat(result.previewData().tags()).isEmpty();
        assertThat(result.previewData().comparisons()).isEmpty();
        assertThat(result.previewData().statistics().dailyValues().getFirst().anxietyMean()).isNull();
    }

    private static ReportSnapshotBuilder builder(ReportSourceData source) {
        var selections = mock(ReportSelectionService.class);
        when(selections.select(any(), any())).thenReturn(source);
        return new ReportSnapshotBuilder(selections, new AnalyticsCalculator(),
                new ReportFactBuilder(JsonMapper.builder().build()), new TemplateOverview(),
                Mappers.getMapper(RatingMapper.class), Mappers.getMapper(EntryMapper.class), Mappers.getMapper(TagMapper.class));
    }

    static CreateReportRequest request(LocalDate day, Set<ReportSection> sections) {
        return new CreateReportRequest(day, day, "UTC", "Демо", sections, EntrySelection.ALL_VISIBLE, List.of(),
                MedicationSelection.NONE, List.of(), List.of(TagCode.ANXIETY));
    }
}
