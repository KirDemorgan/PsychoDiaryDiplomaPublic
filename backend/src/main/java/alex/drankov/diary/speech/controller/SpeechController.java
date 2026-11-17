package alex.drankov.diary.speech.controller;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.security.CurrentUser;
import alex.drankov.diary.speech.dto.SpeechConsentRequest;
import alex.drankov.diary.speech.dto.SpeechConsentResponse;
import alex.drankov.diary.speech.dto.TranscriptionResponse;
import alex.drankov.diary.speech.service.SpeechConsent;
import alex.drankov.diary.speech.service.TranscriptionService;
import alex.drankov.diary.speech.service.WavValidator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/speech")
@RequiredArgsConstructor
@Tag(name = "Голосовой ввод")
public class SpeechController {
    private final TranscriptionService service;
    private final SpeechConsent consent;

    @GetMapping("/consent")
    public ResponseEntity<SpeechConsentResponse> consent() {
        var result = consent.get(CurrentUser.id());
        return ResponseEntity.ok().eTag(Versions.etag(result.version())).body(result);
    }

    @PutMapping("/consent")
    public ResponseEntity<SpeechConsentResponse> consent(
            @RequestHeader("If-Match")
            String ifMatch,
            @Valid
            @RequestBody
            SpeechConsentRequest request
    ) {
        var result = consent.update(CurrentUser.id(), ifMatch, request);
        return ResponseEntity.ok().eTag(Versions.etag(result.version())).body(result);
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Расшифровать русский WAV PCM16 mono 16 kHz, 0,1–120 секунд")
    public ResponseEntity<TranscriptionResponse> create(
            @RequestHeader("Idempotency-Key")
            UUID key,
            @RequestPart("audio")
            MultipartFile file
    ) throws IOException {
        if (file.getSize() > WavValidator.MAX_BYTES) {
            throw new ApiException(413, "AUDIO_TOO_LARGE", "Аудио превышает 4 MiB");
        }
        byte[] audio;
        try (var stream = file.getInputStream()) {
            audio = stream.readNBytes(WavValidator.MAX_BYTES + 1);
        }
        try {
            var result = service.create(CurrentUser.id(), key, audio);
            return ResponseEntity.status(result.status())
                    .location(URI.create("/api/v1/speech/transcriptions/" + result.body().id())).body(result.body());
        } finally {
            Arrays.fill(audio, (byte) 0);
        }
    }

    @GetMapping("/transcriptions/{id}")
    public TranscriptionResponse get(
            @PathVariable
            UUID id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping("/transcriptions/{id}/cancel")
    public TranscriptionResponse cancel(
            @PathVariable
            UUID id) {
        return service.cancel(CurrentUser.id(), id);
    }

    @DeleteMapping("/transcriptions/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable
            UUID id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
