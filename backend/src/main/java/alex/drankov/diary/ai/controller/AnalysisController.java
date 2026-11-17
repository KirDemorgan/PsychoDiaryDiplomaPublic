package alex.drankov.diary.ai.controller;

import alex.drankov.diary.ai.dto.AnalysisResponse;
import alex.drankov.diary.ai.dto.CreateAnalysisRequest;
import alex.drankov.diary.ai.service.AiTaskService;
import alex.drankov.diary.ai.service.AnalysisQuery;
import alex.drankov.diary.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entries/{entryId}/analyses")
@RequiredArgsConstructor
@Tag(name = "AI-анализ записей")
public class AnalysisController {
    private final AiTaskService tasks;
    private final AnalysisQuery query;

    @PostMapping
    public ResponseEntity<AnalysisResponse> start(@PathVariable UUID entryId, @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateAnalysisRequest request) {
        var result = tasks.start(CurrentUser.id(), entryId, key, request);
        return ResponseEntity.status(result.status()).location(URI.create("/api/v1/entries/" + entryId + "/analyses/" + result.body().id())).body(result.body());
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(
            @PathVariable UUID entryId,
            @PathVariable UUID id
    ) {
        return query.get(CurrentUser.id(), entryId, id);
    }

    @PostMapping("/{id}/cancel")
    public AnalysisResponse cancel(
            @PathVariable UUID entryId,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key
    ) {
        return tasks.cancel(CurrentUser.id(), entryId, id, key).body();
    }
}
