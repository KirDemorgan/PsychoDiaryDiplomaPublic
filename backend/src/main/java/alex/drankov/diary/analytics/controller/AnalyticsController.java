package alex.drankov.diary.analytics.controller;

import alex.drankov.diary.analytics.dto.CalendarResponse;
import alex.drankov.diary.analytics.dto.ComparisonResponse;
import alex.drankov.diary.analytics.dto.DayResponse;
import alex.drankov.diary.analytics.dto.OverviewResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.analytics.service.AnalyticsService;
import alex.drankov.diary.security.CurrentUser;
import alex.drankov.diary.tags.entity.TagCode;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Календарь и расчёты наблюдений")
public class AnalyticsController {
    private final AnalyticsService analytics;

    @GetMapping("/analytics/overview")
    public OverviewResponse overview(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone,
            @RequestParam(defaultValue = "7") @Min(0) @Max(10) int highAnxietyThreshold
    ) {
        return analytics.overview(CurrentUser.id(), from, to, timeZone, highAnxietyThreshold);
    }

    @GetMapping("/analytics/tag-comparisons")
    public ComparisonResponse comparison(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone,
            @RequestParam TagCode tagCode,
            @RequestParam Metric metric
    ) {
        return analytics.comparison(CurrentUser.id(), from, to, timeZone, tagCode, metric);
    }

    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone
    ) {
        return analytics.calendar(CurrentUser.id(), from, to, timeZone);
    }

    @GetMapping("/days/{date}")
    public DayResponse day(
            @PathVariable LocalDate date,
            @RequestParam(required = false) String timeZone,
            @RequestParam(defaultValue = "0") @Min(0) int entryPage,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int entrySize
    ) {
        return analytics.day(CurrentUser.id(), date, timeZone, entryPage, entrySize);
    }
}
