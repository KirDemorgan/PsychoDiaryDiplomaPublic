package alex.drankov.diary.reports.service;

import alex.drankov.diary.analytics.dto.ContextTagResponse;
import alex.drankov.diary.analytics.dto.PeriodResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.analytics.service.AnalyticsCalculator;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportPreviewResponse;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.tags.mapper.TagMapper;
import alex.drankov.diary.users.entity.User;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportSnapshotBuilder {
    private final ReportSelectionService selections;
    private final AnalyticsCalculator calculator;
    private final ReportFactBuilder facts;
    private final TemplateOverview template;
    private final RatingMapper ratingMapper;
    private final EntryMapper entryMapper;
    private final TagMapper tagMapper;

    public ReportSnapshot build(
            User owner,
            CreateReportRequest request
    ) {
        var data = selections.select(owner, request);
        var selection = data.selection();
        var period = new PeriodResponse(selection.from(), selection.to(), selection.timeZone());
        var statistics = calculator.overview(period, data.dataRevision(), data.ratings(), data.tags(), data.medications(),
                data.occurrences(), data.intakes(), 7);
        var comparisons = selection.sections().contains(ReportSection.RATINGS) && selection.sections().contains(ReportSection.TAGS)
                ? selection.comparisonTagCodes().stream().flatMap(code -> Arrays.stream(Metric.values())
                .map(metric -> calculator.comparison(period, data.dataRevision(), data.ratings(), data.entries(), data.tags(), code, metric))).toList()
                : List.<alex.drankov.diary.analytics.dto.ComparisonResponse>of();
        var packageFacts = facts.build(data, statistics, comparisons);
        boolean overview = selection.sections().contains(ReportSection.AI_OVERVIEW);
        var preview = new ReportPreviewResponse(statistics, comparisons, data.ratings().stream().map(ratingMapper::response).toList(),
                selection.sections().contains(ReportSection.NOTES) ? data.entries().stream().map(entry -> entryMapper.response(entry,
                        data.tags().stream().filter(tag -> tag.getEntryId().equals(entry.getId())).map(tagMapper::response).toList())).toList() : List.of(),
                data.tags().stream().filter(tag -> tag.getAppliesToLocalDate() != null
                        && !tag.getAppliesToLocalDate().isBefore(selection.from()) && !tag.getAppliesToLocalDate().isAfter(selection.to()))
                        .map(tag -> new ContextTagResponse(tag.getEntryId(), tag.getRevision(), tagMapper.response(tag))).toList(), data.medicationDetails());
        return new ReportSnapshot("diary-report-v1", selection, packageFacts, overview ? template.build(packageFacts) : null,
                preview, overview ? OverviewMode.TEMPLATE : OverviewMode.NONE,
                overview ? List.of("TEMPLATE_PREVIEW") : List.of());
    }
}
