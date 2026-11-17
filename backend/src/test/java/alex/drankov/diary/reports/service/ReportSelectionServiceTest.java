package alex.drankov.diary.reports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.medications.mapper.MedicationMapper;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.service.IntakeService;
import alex.drankov.diary.medications.service.MedicationService;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.tags.repository.EntryTagRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportSelectionServiceTest {
    private final EntryRepository entries = mock(EntryRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private final EntryTagRepository tags = mock(EntryTagRepository.class);
    private final MedicationRepository medications = mock(MedicationRepository.class);
    private final MedicationService medicationService = mock(MedicationService.class);
    private final IntakeRepository intakes = mock(IntakeRepository.class);
    private final ReportSelectionService selections = new ReportSelectionService(entries, ratings, tags, medications,
            medicationService, intakes, mock(IntakeService.class), new MedicationMapper());
    private final LocalDate day = LocalDate.of(2026, 10, 13);

    @Test
    @DisplayName("Скрытая запись исключается до расчётов, NONE не читает записи, снятые разделы не содержат источников")
    void visibilityAndNone() {
        var owner = user();
        var visible = persisted(new DiaryEntry(owner.getId(), "видимое", NOW, "UTC", true));
        var hidden = persisted(new DiaryEntry(owner.getId(), "скрытый маркер", NOW, "UTC", false));
        when(entries.inPeriod(eq(owner.getId()), any(), any())).thenReturn(List.of(visible, hidden));
        var source = selections.select(owner, request(EntrySelection.ALL_VISIBLE, List.of()));
        assertThat(source.entries()).containsExactly(visible);
        assertThat(source.tags()).isEmpty();
        assertThat(source.ratings()).isEmpty();
        assertThat(source.medications()).isEmpty();
        verify(ratings, never()).findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(any(), any(), any());
        assertThat(selections.select(owner, request(EntrySelection.NONE, List.of())).entries()).isEmpty();
    }

    @Test
    @DisplayName("Явно выбранные источники проверяются по владельцу, видимости и периоду")
    void explicitValidation() {
        var owner = user();
        UUID id = UUID.randomUUID();
        var request = request(EntrySelection.EXPLICIT, List.of(id));
        assertThatThrownBy(() -> selections.select(owner, request)).isInstanceOf(ApiException.class)
                .hasMessageContaining("не найден");
        var hidden = persisted(new DiaryEntry(owner.getId(), "скрыто", NOW, "UTC", false));
        when(entries.findByIdAndUserId(id, owner.getId())).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> selections.select(owner, request)).hasMessageContaining("исключена");
        var old = persisted(new DiaryEntry(owner.getId(), "раньше", NOW.minusSeconds(86400), "UTC", true));
        when(entries.findByIdAndUserId(id, owner.getId())).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> selections.select(owner, request)).hasMessageContaining("период");
        var valid = persisted(new DiaryEntry(owner.getId(), "сегодня", NOW, "UTC", true));
        when(entries.findByIdAndUserId(id, owner.getId())).thenReturn(Optional.of(valid));
        assertThat(selections.select(owner, request).entries()).containsExactly(valid);
    }

    @Test
    @DisplayName("Некорректные разделы, повтор ID и ID без EXPLICIT отклоняются до формирования снимка")
    void invalidSelection() {
        var owner = user();
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> selections.select(owner, request(EntrySelection.EXPLICIT, List.of(id, id))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> selections.select(owner, request(EntrySelection.ALL_VISIBLE, List.of(id))))
                .isInstanceOf(ApiException.class);
        var empty = new CreateReportRequest(day, day, "UTC", null, Set.of(), EntrySelection.NONE, List.of(),
                MedicationSelection.NONE, List.of(), List.of());
        assertThatThrownBy(() -> selections.select(owner, empty)).isInstanceOf(ApiException.class);
    }

    private CreateReportRequest request(
            EntrySelection selection,
            List<UUID> ids
    ) {
        return new CreateReportRequest(day, day, "UTC", "Демо", Set.of(ReportSection.NOTES), selection, ids,
                MedicationSelection.NONE, List.of(), List.of());
    }
}
