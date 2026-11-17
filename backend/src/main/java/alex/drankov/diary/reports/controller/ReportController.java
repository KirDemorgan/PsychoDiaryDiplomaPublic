package alex.drankov.diary.reports.controller;

import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportResponse;
import alex.drankov.diary.reports.dto.ReportSummaryResponse;
import alex.drankov.diary.reports.service.ReportService;
import alex.drankov.diary.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Отчёты и PDF")
public class ReportController {
    private final ReportService reports;

    @PostMapping
    public ResponseEntity<ReportResponse> create(@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateReportRequest request) {
        var result = reports.create(CurrentUser.id(), key, request);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/reports/" + result.resourceId()))
                .eTag(Versions.etag(result.version())).body(result.body());
    }

    @GetMapping
    public PageResponse<ReportSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> get(@PathVariable UUID id) {
        return response(reports.get(CurrentUser.id(), id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean includeAiOverview) {
        var value = reports.get(CurrentUser.id(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diary-report-" + value.from() + "-" + value.to() + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(reports.pdf(CurrentUser.id(), id, includeAiOverview));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReportResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key
    ) {
        var value = reports.cancel(CurrentUser.id(), id, key);
        return ResponseEntity.status(value.status()).eTag(Versions.etag(value.version())).body(value.body());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version) {
        reports.delete(CurrentUser.id(), id, version);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ReportResponse> response(ReportResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }
}
