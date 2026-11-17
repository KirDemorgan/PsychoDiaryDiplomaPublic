package alex.drankov.diary.medications.controller;

import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.medications.dto.CreateIntakeRequest;
import alex.drankov.diary.medications.dto.IntakeResponse;
import alex.drankov.diary.medications.dto.OccurrenceListResponse;
import alex.drankov.diary.medications.dto.UpdateIntakeRequest;
import alex.drankov.diary.medications.service.IntakeService;
import alex.drankov.diary.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Плановые дозы и фактические приёмы")
public class IntakeController {
    private final IntakeService intakes;

    @GetMapping("/medication-occurrences")
    public OccurrenceListResponse occurrences(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone, @RequestParam(required = false) UUID medicationId) {
        return intakes.occurrences(CurrentUser.id(), from, to, timeZone, medicationId);
    }

    @GetMapping("/intakes")
    public PageResponse<IntakeResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone, @RequestParam(required = false) UUID medicationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return intakes.list(CurrentUser.id(), from, to, timeZone, medicationId, page, size);
    }

    @PostMapping("/intakes")
    public ResponseEntity<IntakeResponse> create(@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateIntakeRequest request) {
        var result = intakes.create(CurrentUser.id(), key, request);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/intakes/" + result.resourceId()))
                .eTag(Versions.etag(result.version())).body(result.body());
    }

    @GetMapping("/intakes/{id}")
    public ResponseEntity<IntakeResponse> get(@PathVariable UUID id) {
        return response(intakes.get(CurrentUser.id(), id));
    }

    @PatchMapping("/intakes/{id}")
    public ResponseEntity<IntakeResponse> patch(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody UpdateIntakeRequest request) {
        return response(intakes.patch(CurrentUser.id(), id, version, request));
    }

    @DeleteMapping("/intakes/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version) {
        intakes.delete(CurrentUser.id(), id, version);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<IntakeResponse> response(IntakeResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }
}
