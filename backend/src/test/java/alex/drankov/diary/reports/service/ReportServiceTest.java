package alex.drankov.diary.reports.service;

import alex.drankov.diary.ai.service.AiConsent;
import alex.drankov.diary.ai.service.AiTaskService;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.Report;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.render.ReportPdfRenderer;
import alex.drankov.diary.reports.repository.ReportRepository;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.repository.UserSettingsRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReportSnapshotBuilder snapshots = mock(ReportSnapshotBuilder.class);
    private final ReportPdfRenderer renderer = mock(ReportPdfRenderer.class);
    private final UserAccess access = mock(UserAccess.class);
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final Idempotency idempotency = mock(Idempotency.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final AiConsent consent = new AiConsent(settings);
    private final ReportService service = new ReportService(reports, snapshots, renderer, access, idempotency, json, mock(AiTaskService.class), consent);
    private final User owner = user();
    private final LocalDate day = LocalDate.of(2026, 10, 13);
    private final CreateReportRequest request = new CreateReportRequest(day, day, "UTC", null,
            Set.of(ReportSection.RATINGS), EntrySelection.NONE, List.of(), MedicationSelection.NONE, List.of(), List.of());
    private final ReportSnapshot snapshot = new ReportSnapshot("diary-report-v1", request, List.of(), null,
            null, OverviewMode.NONE, List.of());

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(consent, "currentVersion", "v1");
        when(access.lock(owner.getId())).thenReturn(owner);
        when(access.read(owner.getId())).thenReturn(owner);
        when(idempotency.execute(any(), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
    }

    @Test
    @DisplayName("Создание сохраняет согласованный снимок, чтение и список возвращают ту же ревизию")
    void lifecycle() {
        when(snapshots.build(owner, request)).thenReturn(snapshot);
        when(reports.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        var result = service.create(owner.getId(), UUID.randomUUID(), request);
        assertThat(result.status()).isEqualTo(202);
        assertThat(result.body().stale()).isFalse();
        var report = stored();
        when(reports.findByIdAndUserId(report.getId(), owner.getId())).thenReturn(Optional.of(report));
        when(reports.findByUserId(eq(owner.getId()), any())).thenReturn(new PageImpl<>(List.of(report)));
        assertThat(service.get(owner.getId(), report.getId()).inputHash()).isEqualTo(report.getInputHash());
        assertThat(service.list(owner.getId(), 0, 20).items()).hasSize(1);
        when(renderer.render(any(), eq(false))).thenReturn(new byte[]{1, 2});
        assertThat(service.pdf(owner.getId(), report.getId(), false)).containsExactly((byte) 1, (byte) 2);
        owner.advanceDataRevision();
        assertThat(service.get(owner.getId(), report.getId()).stale()).isTrue();
        assertThatThrownBy(() -> service.pdf(owner.getId(), report.getId(), true)).hasMessageContaining("Источники изменились");
    }

    @Test
    @DisplayName("Раздел обзора требует включённого AI и актуального согласия до формирования пакета")
    void consent() {
        var overviewRequest = new CreateReportRequest(day, day, "UTC", null, Set.of(ReportSection.AI_OVERVIEW),
                EntrySelection.NONE, List.of(), MedicationSelection.NONE, List.of(), List.of());
        var config = new UserSettings(owner.getId());
        when(settings.findByUserId(owner.getId())).thenReturn(Optional.of(config));
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), overviewRequest)).hasMessageContaining("отключён");
        config.ai(true);
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), overviewRequest)).hasMessageContaining("согласие");
        config.consent("v1", NOW);
        when(snapshots.build(owner, overviewRequest)).thenReturn(snapshot);
        when(reports.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        assertThat(service.create(owner.getId(), UUID.randomUUID(), overviewRequest).body().id()).isNotNull();
    }

    @Test
    @DisplayName("Чужой отчёт не раскрывается; готовый нельзя отменить; удаление требует If-Match и стирает receipt")
    void ownershipAndDeletion() {
        assertThatThrownBy(() -> service.get(owner.getId(), UUID.randomUUID())).isInstanceOf(ApiException.class);
        var report = stored();
        when(reports.findByIdAndUserId(report.getId(), owner.getId())).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> service.cancel(owner.getId(), report.getId(), UUID.randomUUID())).hasMessageContaining("Готовый");
        assertThatThrownBy(() -> service.delete(owner.getId(), report.getId(), null)).isInstanceOf(ApiException.class);
        service.delete(owner.getId(), report.getId(), "\"v0\"");
        verify(idempotency).erase(owner.getId(), report.getId());
        verify(reports).delete(report);
        verify(renderer, never()).render(any(), anyBoolean());
    }

    private Report stored() {
        return persisted(new Report(owner.getId(), day, day, "UTC", owner.getDataRevision(), "hash",
                json.writeValueAsString(snapshot)));
    }

    @Test
    @DisplayName("Очищенный устаревший снимок не возвращает удалённый текст, факты и обзор")
    void scrubbedSnapshot() {
        var report = stored();
        ReflectionTestUtils.setField(report, "snapshotJson", null);
        ReflectionTestUtils.setField(report, "errorCode", "SOURCE_DATA_CHANGED");
        owner.advanceDataRevision();
        when(reports.findByIdAndUserId(report.getId(), owner.getId())).thenReturn(Optional.of(report));
        var result = service.get(owner.getId(), report.getId());
        assertThat(result.stale()).isTrue();
        assertThat(result.facts()).isEmpty();
        assertThat(result.previewData()).isNull();
        assertThat(result.overview()).isNull();
        assertThat(result.selection()).isNull();
    }
}
