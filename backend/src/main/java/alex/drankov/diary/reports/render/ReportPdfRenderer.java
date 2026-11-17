package alex.drankov.diary.reports.render;

import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.entity.ReportSection;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
@RequiredArgsConstructor
public class ReportPdfRenderer {
    private final ReportChartRenderer charts;
    private final TemplateEngine templates = templates();

    public byte[] render(
            ReportSnapshot snapshot,
            boolean includeAiOverview
    ) {
        var context = new Context(Locale.forLanguageTag("ru"));
        context.setVariable("report", snapshot);
        context.setVariable("showOverview", includeAiOverview && snapshot.overview() != null);
        context.setVariable("showRatings", snapshot.selection().sections().contains(ReportSection.RATINGS));
        context.setVariable("showTags", snapshot.selection().sections().contains(ReportSection.TAGS));
        context.setVariable("showMedications", snapshot.selection().sections().contains(ReportSection.MEDICATIONS));
        context.setVariable("showNotes", snapshot.selection().sections().contains(ReportSection.NOTES));
        context.setVariable("anxietyChart", charts.chart(snapshot.previewData().statistics().dailyValues(), Metric.ANXIETY));
        context.setVariable("moodChart", charts.chart(snapshot.previewData().statistics().dailyValues(), Metric.MOOD));
        context.setVariable("energyChart", charts.chart(snapshot.previewData().statistics().dailyValues(), Metric.ENERGY));
        context.setVariable("coverage", snapshot.previewData().statistics().coverage().metrics().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        context.setVariable("labels", Map.ofEntries(
                Map.entry("MORNING", "Утро"), Map.entry("DAY", "День"), Map.entry("EVENING", "Вечер"),
                Map.entry("ANXIETY", "Тревога"), Map.entry("MOOD", "Настроение"), Map.entry("ENERGY", "Энергия"),
                Map.entry("PRESENT", "Присутствует"), Map.entry("NEGATED", "Отрицание"), Map.entry("UNCERTAIN", "Неясно"),
                Map.entry("CURRENT", "Текущее"), Map.entry("PAST", "Прошлое"), Map.entry("FUTURE", "Будущее"),
                Map.entry("UNSPECIFIED", "Не уточнено"), Map.entry("SELF", "О себе"), Map.entry("OTHER", "О другом"),
                Map.entry("TAKEN", "Принято"), Map.entry("SKIPPED", "Явно пропущено"), Map.entry("NO_DATA", "Нет отметки"),
                Map.entry("MG", "мг"), Map.entry("G", "г"), Map.entry("ML", "мл"),
                Map.entry("TABLET", "табл."), Map.entry("DROP", "кап."), Map.entry("UNIT", "ед.")));
        context.setVariable("logo", logo());
        String html = templates.process("report", context);
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.useFont(() -> resource("/fonts/DejaVuSans.ttf"), "Diary", 400,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> resource("/fonts/DejaVuSans-Bold.ttf"), "Diary", 700,
                    com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:image/png;base64,"),
                    ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:image/png;base64,"),
                    ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("PDF rendering failed", exception);
        }
    }

    private static TemplateEngine templates() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/reports/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        var engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static InputStream resource(String path) {
        return Objects.requireNonNull(ReportPdfRenderer.class.getResourceAsStream(path), "Missing report resource");
    }

    private static String logo() {
        try (var input = resource("/reports/diary-mark.png")) {
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(input.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Report logo unavailable", exception);
        }
    }
}
