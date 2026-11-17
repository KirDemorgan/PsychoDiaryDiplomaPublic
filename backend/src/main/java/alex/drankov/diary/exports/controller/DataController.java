package alex.drankov.diary.exports.controller;

import alex.drankov.diary.exports.dto.DeleteAccountRequest;
import alex.drankov.diary.exports.dto.ExportJobResponse;
import alex.drankov.diary.exports.service.AccountDeletionService;
import alex.drankov.diary.exports.service.DataExportService;
import alex.drankov.diary.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Экспорт и удаление данных")
public class DataController {
    private final DataExportService exports;
    private final AccountDeletionService accounts;

    @PostMapping("/data-exports")
    public ResponseEntity<ExportJobResponse> create(@RequestHeader("Idempotency-Key") UUID key) {
        var result = exports.create(CurrentUser.id(), key);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/data-exports/" + result.resourceId())).body(result.body());
    }

    @GetMapping("/data-exports/{id}")
    public ExportJobResponse get(@PathVariable UUID id) {
        return exports.get(CurrentUser.id(), id);
    }

    @GetMapping(value = "/data-exports/{id}/file", produces = "application/zip")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diary-data.zip\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(exports.file(CurrentUser.id(), id));
    }

    @DeleteMapping("/data-exports/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        exports.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @Operation(description = "Удаляет аккаунт и все связанные данные; JSON body: currentPassword и confirm=true.")
    public ResponseEntity<Void> account(@RequestBody DeleteAccountRequest request) {
        accounts.delete(CurrentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
