package alex.drankov.diary.speech.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.speech.entity.Transcription;
import alex.drankov.diary.speech.entity.TranscriptionStatus;
import alex.drankov.diary.speech.repository.TranscriptionRepository;
import alex.drankov.diary.speech.service.model.SpeechWorkItem;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeechQueue {
    private final TranscriptionRepository tasks;
    private final UserAccess access;
    private final SpeechConsent consent;
    private final Clock clock;
    private final Idempotency idempotency;

    @Value("${app.speech.timeout:PT60S}")
    private Duration timeout;

    @Transactional
    public SpeechWorkItem claim() {
        var identities = tasks.runnable(clock.instant(), PageRequest.of(0, 1));
        if (identities.isEmpty()) {
            return null;
        }
        var identity = identities.getFirst();
        if (!lock(identity.userId())) {
            return null;
        }
        var task = tasks.findByIdAndUserId(identity.id(), identity.userId()).orElse(null);
        if (task == null || !task.runnable(clock.instant()) || invalidate(task)) {
            return null;
        }
        if (task.getAttempt() >= 2) {
            task.finish(TranscriptionStatus.FAILED, "WORKER_INTERRUPTED", clock.instant());
            return null;
        }
        var token = UUID.randomUUID();
        task.claim(token, clock.instant().plus(timeout).plusSeconds(30));
        tasks.flush();
        return new SpeechWorkItem(task.getId(), token, task.getAudio().clone(), task.getModelId());
    }

    @Transactional
    public void succeed(SpeechWorkItem work, String text) {
        var task = publishing(work);
        if (task != null) {
            task.succeed(text, clock.instant());
        }
    }

    @Transactional
    public void fail(SpeechWorkItem work, String code) {
        var task = publishing(work);
        if (task == null) {
            return;
        }
        if (task.getAttempt() < 2 && !Set.of("SPEECH_MODEL_NOT_CONFIGURED", "MODEL_CHANGED", "NO_SPEECH").contains(code)) {
            task.retry(code);
        } else {
            task.finish(TranscriptionStatus.FAILED, code, clock.instant());
        }
    }

    private Transcription publishing(SpeechWorkItem work) {
        var identity = tasks.identity(work.id()).orElse(null);
        if (identity == null || !lock(identity.userId())) {
            return null;
        }
        var task = tasks.findByIdAndUserId(identity.id(), identity.userId()).orElse(null);
        if (task == null || task.getStatus() != TranscriptionStatus.RUNNING
                || !work.leaseToken().equals(task.getLeaseToken()) || !task.getLeaseUntil().isAfter(clock.instant())
                || invalidate(task)) {
            return null;
        }
        return task;
    }

    private boolean invalidate(Transcription task) {
        if (!task.getExpiresAt().isAfter(clock.instant())) {
            idempotency.erase(task.getUserId(), task.getId());
            task.finish(TranscriptionStatus.EXPIRED, "TRANSCRIPTION_EXPIRED", clock.instant());
            return true;
        }
        if (!consent.allowed(task.getUserId())) {
            task.finish(TranscriptionStatus.CANCELLED, "SPEECH_CONSENT_REVOKED", clock.instant());
            return true;
        }
        return false;
    }

    private boolean lock(UUID user) {
        try {
            access.lock(user);
            return true;
        } catch (ApiException exception) {
            if (exception.getStatus() != 404) {
                throw exception;
            }
            return false;
        }
    }
}
