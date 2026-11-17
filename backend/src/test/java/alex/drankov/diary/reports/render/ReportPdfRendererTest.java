package alex.drankov.diary.reports.render;

import alex.drankov.diary.analytics.dto.ContextTagResponse;
import alex.drankov.diary.analytics.dto.PeriodResponse;
import alex.drankov.diary.analytics.service.AnalyticsCalculator;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.dto.ScheduleSlotResponse;
import alex.drankov.diary.medications.dto.ScheduleVersionResponse;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportMedicationResponse;
import alex.drankov.diary.reports.dto.ReportPreviewResponse;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.service.TemplateOverview;
import alex.drankov.diary.tags.dto.TagResponse;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class ReportPdfRendererTest {
    @Test
    @DisplayName("Шаблон PDF печатает кириллицу, графики и длинные записи, экранирует HTML и позволяет убрать обзор")
    void templateLayoutAndEscaping() throws Exception {
        var owner = user();
        var from = LocalDate.of(2026, 10, 1);
        var to = from.plusDays(6);
        var ratings = new java.util.ArrayList<StateRating>();
        for (int i : new int[]{0, 1, 3, 4, 6}) {
            var rating = persisted(new StateRating(owner.getId(), from.plusDays(i), RatingSlot.MORNING));
            rating.replace(i, 10 - i, 5, "UTC");
            ratings.add(rating);
        }
        var entry = persisted(new DiaryEntry(owner.getId(), "Сегодня тревожно. Синтетическая запись. 😀 <img src='http://localhost/secret'/>\n"
                + "Наблюдения за своим состоянием, без медицинских выводов. ".repeat(90), from.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC), "UTC", true));
        var stats = new AnalyticsCalculator().overview(new PeriodResponse(from, to, "UTC"), 0, ratings,
                List.of(), List.of(), List.of(), List.of(), 7);
        var selection = new CreateReportRequest(from, to, "UTC", "Демо-пользователь", Set.of(ReportSection.values()),
                EntrySelection.ALL_VISIBLE, List.of(), MedicationSelection.ALL, List.of(), List.of());
        var overview = new TemplateOverview().build(List.of());
        UUID medicationId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        var scheme = new ScheduleVersionResponse(versionId, medicationId, NOW.minusSeconds(86400 * 30), null,
                null, "UTC", List.of(new ScheduleSlotResponse(slotId, LocalTime.NOON, Set.of(DayOfWeek.MONDAY),
                BigDecimal.ONE, DoseUnit.TABLET)), NOW, 0);
        var occurrence = new OccurrenceResponse("demo", medicationId, "Синтетический препарат", versionId,
                slotId, from.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC), BigDecimal.ONE, DoseUnit.TABLET, "UTC", IntakeStatus.NO_DATA, null);
        var medication = new ReportMedicationResponse(medicationId, "Синтетический препарат", "Таблетки", null,
                List.of(scheme), List.of(occurrence), List.of());
        var tag = new TagResponse(UUID.randomUUID(), TagCode.ANXIETY, "STATE", "тревожно", 8, 16,
                TagPresence.UNCERTAIN, TagTemporal.CURRENT, TagSubject.SELF, from, TagOrigin.MANUAL);
        var preview = new ReportPreviewResponse(stats, List.of(), ratings.stream().map(Mappers.getMapper(RatingMapper.class)::response).toList(),
                List.of(Mappers.getMapper(EntryMapper.class).response(entry)),
                List.of(new ContextTagResponse(entry.getId(), 1, tag)), List.of(medication));
        var snapshot = new ReportSnapshot("diary-report-v1", selection, List.of(), overview, preview, OverviewMode.TEMPLATE, List.of());
        var renderer = new ReportPdfRenderer(new ReportChartRenderer());
        byte[] bytes = renderer.render(snapshot, true);
        assertThat(bytes).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Дневник состояния", "Демо-пользователь", "Синтетическая запись", "Нейросетевая генерация не использована");
            assertThat(text).contains("Синтетический препарат", "Неясно", "Нет отметки", "Утро");
            assertThat(text).contains("<img src='http://localhost/secret'/>");
            assertThat(document.getNumberOfPages()).isGreaterThan(3);
            var directory = Path.of("target/pdf-preview");
            Files.createDirectories(directory);
            Files.write(directory.resolve("diary-report-demo.pdf"), bytes);
            var pages = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                javax.imageio.ImageIO.write(pages.renderImageWithDPI(i, 110), "png", directory.resolve("page-" + (i + 1) + ".png").toFile());
            }
        }
        try (var document = Loader.loadPDF(renderer.render(snapshot, false))) {
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("AI-обзор наблюдений для обсуждения с врачом");
        }
    }
}
