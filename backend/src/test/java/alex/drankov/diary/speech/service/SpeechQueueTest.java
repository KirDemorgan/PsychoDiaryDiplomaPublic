package alex.drankov.diary.speech.service;

import alex.drankov.diary.TestFixtures;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.speech.WavFixtures;
import alex.drankov.diary.speech.entity.Transcription;
import alex.drankov.diary.speech.entity.TranscriptionStatus;
import alex.drankov.diary.speech.repository.TranscriptionRepository;
import alex.drankov.diary.speech.service.model.SpeechWorkItem;
import alex.drankov.diary.speech.service.model.TranscriptionIdentity;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeechQueueTest {
    private final TranscriptionRepository tasks = mock(TranscriptionRepository.class);
    private final UserAccess access = mock(UserAccess.class);
    private final SpeechConsent consent = mock(SpeechConsent.class);
    private final SpeechQueue queue = new SpeechQueue(tasks, access, consent, TestFixtures.CLOCK, mock(Idempotency.class));
    private final UUID user = UUID.randomUUID();
    private final Transcription task = TestFixtures.persisted(new Transcription(user, WavFixtures.wav(16000),
            "a".repeat(64), 1000, "speech-test", TestFixtures.NOW.plusSeconds(900)));

    private void available() {
        ReflectionTestUtils.setField(queue, "timeout", Duration.ofSeconds(60));
        var identity = new TranscriptionIdentity(task.getId(), user);
        when(tasks.runnable(any(), any())).thenReturn(List.of(identity));
        when(tasks.identity(task.getId())).thenReturn(Optional.of(identity));
        when(tasks.findByIdAndUserId(task.getId(), user)).thenReturn(Optional.of(task));
        when(consent.allowed(user)).thenReturn(true);
    }

    @Test
    @DisplayName("Worker получает отдельную копию WAV и lease; успешная публикация удаляет аудио и хранит текст сутки")
    void success() {
        available();
        var work = queue.claim();
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.RUNNING);
        assertThat(work.audio()).isNotSameAs(task.getAudio());
        queue.succeed(work, "Синтетическая заметка");
        assertThat(task.getAudio()).isNull();
        assertThat(task.getTranscript()).isEqualTo("Синтетическая заметка");
        assertThat(task.getExpiresAt()).isEqualTo(TestFixtures.NOW.plusSeconds(86400));
    }

    @Test
    @DisplayName("Переходная ошибка допускает один повтор; второй сбой удаляет аудио")
    void retry() {
        available();
        queue.fail(queue.claim(), "SPEECH_TIMEOUT");
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.QUEUED);
        assertThat(task.getAudio()).isNotNull();
        queue.fail(queue.claim(), "SPEECH_TIMEOUT");
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(task.getAttempt()).isEqualTo(2);
        assertThat(task.getAudio()).isNull();
    }

    @Test
    @DisplayName("Отменённая задача и ответ старого lease не публикуются")
    void staleLease() {
        available();
        var work = queue.claim();
        queue.succeed(new SpeechWorkItem(work.id(), UUID.randomUUID(), work.audio(), work.modelId()), "Чужой ответ");
        assertThat(task.getTranscript()).isNull();
        task.finish(TranscriptionStatus.CANCELLED, "USER_CANCELLED", TestFixtures.NOW);
        queue.succeed(work, "Поздний ответ");
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.CANCELLED);
        assertThat(task.getTranscript()).isNull();
    }

    @Test
    @DisplayName("Отозванное согласие и истёкший TTL запрещают публикацию и стирают аудио")
    void revokedAndExpired() {
        available();
        var work = queue.claim();
        when(consent.allowed(user)).thenReturn(false);
        queue.succeed(work, "Текст");
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.CANCELLED);
        ReflectionTestUtils.setField(task, "status", TranscriptionStatus.QUEUED);
        ReflectionTestUtils.setField(task, "expiresAt", TestFixtures.NOW);
        assertThat(queue.claim()).isNull();
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Истёкший lease восстанавливается; после двух захватов задача завершается без третьей попытки")
    void leaseRecovery() {
        available();
        task.claim(UUID.randomUUID(), TestFixtures.NOW.minusSeconds(1));
        assertThat(queue.claim()).isNotNull();
        ReflectionTestUtils.setField(task, "leaseUntil", TestFixtures.NOW);
        assertThat(queue.claim()).isNull();
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
    }

    @Test
    @DisplayName("Пустая очередь и удалённая задача не вызывают модель")
    void empty() {
        assertThat(queue.claim()).isNull();
        available();
        when(tasks.findByIdAndUserId(task.getId(), user)).thenReturn(Optional.empty());
        assertThat(queue.claim()).isNull();
        queue.succeed(new SpeechWorkItem(UUID.randomUUID(), UUID.randomUUID(), new byte[0], "test"), "Текст");
    }
}
