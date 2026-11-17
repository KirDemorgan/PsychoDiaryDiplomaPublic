package alex.drankov.diary.speech.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transcription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transcription extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TranscriptionStatus status = TranscriptionStatus.QUEUED;

    @Column(name = "audio", columnDefinition = "bytea")
    private byte[] audio;

    @Column(name = "audio_hash", nullable = false, length = 64)
    private String audioHash;

    @Column(name = "duration_millis", nullable = false)
    private long durationMillis;

    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    public Transcription(UUID user, byte[] audio, String hash, long duration, String model, Instant expires) {
        userId = user;
        this.audio = audio.clone();
        audioHash = hash;
        durationMillis = duration;
        modelId = model;
        expiresAt = expires;
    }

    public boolean active() {
        return status == TranscriptionStatus.QUEUED || status == TranscriptionStatus.RUNNING;
    }

    public boolean runnable(Instant now) {
        return status == TranscriptionStatus.QUEUED
                || status == TranscriptionStatus.RUNNING && !leaseUntil.isAfter(now);
    }

    public void claim(UUID token, Instant until) {
        status = TranscriptionStatus.RUNNING;
        leaseToken = token;
        leaseUntil = until;
        attempt++;
        errorCode = null;
    }

    public void retry(String code) {
        status = TranscriptionStatus.QUEUED;
        errorCode = code;
        leaseToken = null;
        leaseUntil = null;
    }

    public void succeed(String text, Instant now) {
        finish(TranscriptionStatus.SUCCEEDED, null, now);
        transcript = text;
        expiresAt = now.plusSeconds(86400);
    }

    public void finish(TranscriptionStatus state, String code, Instant now) {
        status = state;
        errorCode = code;
        finishedAt = now;
        audio = null;
        transcript = null;
        leaseToken = null;
        leaseUntil = null;
    }
}
