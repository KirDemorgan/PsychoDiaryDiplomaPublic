package alex.drankov.diary.medications.controller;

import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.medications.dto.CreateMedicationRequest;
import alex.drankov.diary.medications.dto.CreateScheduleRequest;
import alex.drankov.diary.medications.dto.MedicationResponse;
import alex.drankov.diary.medications.dto.ScheduleVersionResponse;
import alex.drankov.diary.medications.dto.UpdateMedicationRequest;
import alex.drankov.diary.medications.service.MedicationService;
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
@RequestMapping("/api/v1/medications")
@RequiredArgsConstructor
@Tag(name = "Препараты и версии расписаний")
public class MedicationController {
    private final MedicationService medications;

    @PostMapping
    public ResponseEntity<MedicationResponse> create(@RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateMedicationRequest request) {
        var result = medications.create(CurrentUser.id(), key, request);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/medications/" + result.resourceId()))
                .eTag(Versions.etag(result.version())).body(result.body());
    }

    @GetMapping
    public PageResponse<MedicationResponse> list(@RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return medications.list(CurrentUser.id(), archived, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicationResponse> get(@PathVariable UUID id) {
        return response(medications.get(CurrentUser.id(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MedicationResponse> patch(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody UpdateMedicationRequest request) {
        return response(medications.patch(CurrentUser.id(), id, version, request));
    }

    @GetMapping("/{id}/schedule-versions")
    public List<ScheduleVersionResponse> history(@PathVariable UUID id) {
        return medications.history(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/schedule-versions")
    public ResponseEntity<ScheduleVersionResponse> schedule(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestHeader("Idempotency-Key") UUID key, @RequestBody CreateScheduleRequest request) {
        var result = medications.schedule(CurrentUser.id(), id, version, key, request);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/medications/" + id + "/schedule-versions"))
                .eTag(Versions.etag(result.version())).body(result.body());
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<MedicationResponse> archive(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestHeader("Idempotency-Key") UUID key) {
        var result = medications.archive(CurrentUser.id(), id, version, key);
        return ResponseEntity.status(result.status()).eTag(Versions.etag(result.version())).body(result.body());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version) {
        medications.delete(CurrentUser.id(), id, version);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<MedicationResponse> response(MedicationResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }
}
