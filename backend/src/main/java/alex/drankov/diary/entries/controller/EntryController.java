package alex.drankov.diary.entries.controller;

import alex.drankov.diary.entries.dto.UpdateEntryRequest;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.entries.dto.CreateEntryRequest;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.dto.EntrySummaryResponse;
import alex.drankov.diary.entries.service.EntryService;
import alex.drankov.diary.security.CurrentUser;
import alex.drankov.diary.tags.entity.TagCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.net.URI;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/entries")
@RequiredArgsConstructor
@Tag(name = "Записи дневника")
public class EntryController {
    private final EntryService entries;

    @PostMapping
    public ResponseEntity<EntryResponse> create(
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody CreateEntryRequest request
    ) {
        var value = entries.create(CurrentUser.id(), key, request);
        return ResponseEntity.status(value.status()).location(URI.create("/api/v1/entries/" + value.resourceId()))
                .eTag(Versions.etag(value.version())).body(value.body());
    }

    @GetMapping
    @Operation(description = "Сортировка occurredAt DESC, id DESC. from/to передаются парой, до 366 дней. confirmedTag учитывает подтверждённые присутствующие признаки текущей редакции.")
    public PageResponse<EntrySummaryResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TagCode confirmedTag,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return entries.list(CurrentUser.id(), from, to, timeZone, q, confirmedTag, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntryResponse> get(@PathVariable UUID id) {
        return response(entries.get(CurrentUser.id(), id));
    }

    @PatchMapping("/{id}")
    @Operation(description = "Изменяемые поля: text, occurredAt, timeZone, reportVisible. Правка текста/времени создаёт новую revision. If-Match обязателен.")
    public ResponseEntity<EntryResponse> patch(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody UpdateEntryRequest body
    ) {
        return response(entries.patch(CurrentUser.id(), id, version, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version
    ) {
        entries.delete(CurrentUser.id(), id, version);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<EntryResponse> response(EntryResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }
}
