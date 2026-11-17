package alex.drankov.diary.ratings.controller;

import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.ratings.dto.PutRatingRequest;
import alex.drankov.diary.ratings.dto.RatingResponse;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.service.RatingService;
import alex.drankov.diary.security.CurrentUser;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ratings")
@RequiredArgsConstructor
@Tag(name = "Оценки состояния")
public class RatingController {
    private final RatingService ratings;

    @GetMapping
    public PageResponse<RatingResponse> list(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) RatingSlot slot,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ratings.list(CurrentUser.id(), from, to, slot, page, size);
    }

    @GetMapping("/{date}/{slot}")
    public ResponseEntity<RatingResponse> get(@PathVariable LocalDate date, @PathVariable RatingSlot slot) {
        var value = ratings.get(CurrentUser.id(), date, slot);
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }

    @PutMapping("/{date}/{slot}")
    @Operation(description = "Все три показателя обязательны, каждый допускает null. Для существующего ресурса нужен If-Match.")
    public ResponseEntity<RatingResponse> put(
            @PathVariable LocalDate date,
            @PathVariable RatingSlot slot,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestHeader(value = "If-Match", required = false) String version,
            @Valid @RequestBody PutRatingRequest body
    ) {
        var result = ratings.put(CurrentUser.id(), date, slot, key, version, body);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/ratings/" + date + "/" + slot))
                .eTag(Versions.etag(result.version())).body(result.body());
    }

    @DeleteMapping("/{date}/{slot}")
    public ResponseEntity<Void> delete(
            @PathVariable LocalDate date,
            @PathVariable RatingSlot slot,
            @RequestHeader(value = "If-Match", required = false) String version
    ) {
        ratings.delete(CurrentUser.id(), date, slot, version);
        return ResponseEntity.noContent().build();
    }
}
