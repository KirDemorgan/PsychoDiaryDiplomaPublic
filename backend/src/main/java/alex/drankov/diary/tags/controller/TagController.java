package alex.drankov.diary.tags.controller;

import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.security.CurrentUser;
import alex.drankov.diary.tags.dto.TagDefinitionsResponse;
import alex.drankov.diary.tags.dto.TagReviewRequest;
import alex.drankov.diary.tags.service.TagService;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Словарь и подтверждение тегов")
public class TagController {
    private final TagService tags;

    @GetMapping("/tag-definitions")
    public TagDefinitionsResponse definitions() {
        return tags.definitions();
    }

    @PutMapping("/entries/{id}/tag-review")
    public ResponseEntity<EntryResponse> review(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestHeader("Idempotency-Key") UUID key, @RequestBody TagReviewRequest request) {
        var result = tags.review(CurrentUser.id(), id, version, key, request);
        return ResponseEntity.status(result.status()).eTag(Versions.etag(result.version())).body(result.body());
    }
}
