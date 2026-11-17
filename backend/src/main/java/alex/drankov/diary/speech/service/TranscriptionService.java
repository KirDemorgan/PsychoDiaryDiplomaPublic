package alex.drankov.diary.speech.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.speech.adapter.SpeechAdapter;
import alex.drankov.diary.speech.dto.TranscriptionResponse;
import alex.drankov.diary.speech.entity.Transcription;
import alex.drankov.diary.speech.entity.TranscriptionStatus;
import alex.drankov.diary.speech.mapper.TranscriptionMapper;
import alex.drankov.diary.speech.repository.TranscriptionRepository;
import alex.drankov.diary.speech.service.model.TranscriptionIdentity;
import alex.drankov.diary.users.service.UserAccess;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TranscriptionService {
    private final TranscriptionRepository tasks;
    private final UserAccess access;
    private final SpeechConsent consent;
    private final WavValidator validator;
    private final TranscriptionMapper mapper;
    private final Idempotency idempotency;
    private final SpeechAdapter model;
    private final Clock clock;

    @Transactional
    public MutationResult<TranscriptionResponse> create(UUID user, UUID key, byte[] audio) {
        long duration = validator.validate(audio);
        String hash = hash(audio);
        access.lock(user);
        consent.require(user);
        return idempotency.execute(user, "POST /speech/transcriptions", key, hash, TranscriptionResponse.class, () -> {
            if (!model.available()) {
                throw new ApiException(503, "SPEECH_MODEL_NOT_CONFIGURED", "Речевая модель не подключена");
            }
            if (tasks.existsByUserIdAndStatusIn(user, Set.of(TranscriptionStatus.QUEUED, TranscriptionStatus.RUNNING))) {
                throw new ApiException(409, "TRANSCRIPTION_ALREADY_ACTIVE", "Дождитесь завершения или отмените текущую расшифровку");
            }
            if (tasks.countByUserIdAndCreatedAtAfter(user, clock.instant().minusSeconds(3600)) >= 30) {
                throw new ApiException(429, "SPEECH_RATE_LIMIT", "Превышен лимит расшифровок за час");
            }
            var task = tasks.saveAndFlush(new Transcription(user, audio, hash, duration, model.modelId(),
                    clock.instant().plusSeconds(900)));
            return new MutationResult<>(mapper.response(task), task.getId(), task.getVersion(), 202);
        });
    }

    @Transactional(readOnly = true)
    public TranscriptionResponse get(UUID user, UUID id) {
        var task = owned(user, id);
        if (!task.getExpiresAt().isAfter(clock.instant()) || task.getStatus() == TranscriptionStatus.EXPIRED) {
            throw new ApiException(410, "TRANSCRIPTION_EXPIRED", "Срок хранения расшифровки истёк");
        }
        consent.require(user);
        return mapper.response(task);
    }

    @Transactional
    public TranscriptionResponse cancel(UUID user, UUID id) {
        access.lock(user);
        var task = owned(user, id);
        if (!task.active() && task.getStatus() != TranscriptionStatus.CANCELLED) {
            throw new ApiException(409, "TRANSCRIPTION_NOT_CANCELLABLE", "Задача уже завершена");
        }
        task.finish(TranscriptionStatus.CANCELLED, "USER_CANCELLED", clock.instant());
        tasks.flush();
        return mapper.response(task);
    }

    @Transactional
    public void delete(UUID user, UUID id) {
        access.lock(user);
        var task = owned(user, id);
        idempotency.erase(user, id);
        tasks.delete(task);
    }

    @Transactional
    public void expire(TranscriptionIdentity identity) {
        if (!lock(identity.userId())) {
            return;
        }
        var task = tasks.findByIdAndUserId(identity.id(), identity.userId()).orElse(null);
        if (task == null || task.getExpiresAt().isAfter(clock.instant())) {
            return;
        }
        idempotency.erase(identity.userId(), identity.id());
        if (task.getStatus() == TranscriptionStatus.EXPIRED
                && !task.getExpiresAt().isAfter(clock.instant().minusSeconds(7 * 86400))) {
            tasks.delete(task);
        } else {
            task.finish(TranscriptionStatus.EXPIRED, "TRANSCRIPTION_EXPIRED", clock.instant());
        }
    }

    private Transcription owned(UUID user, UUID id) {
        return tasks.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
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

    private String hash(byte[] audio) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(audio));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
