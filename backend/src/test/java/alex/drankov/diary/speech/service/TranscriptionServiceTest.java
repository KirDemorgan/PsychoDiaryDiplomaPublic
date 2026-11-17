package alex.drankov.diary.speech.service;

import alex.drankov.diary.TestFixtures;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.speech.WavFixtures;
import alex.drankov.diary.speech.adapter.SpeechAdapter;
import alex.drankov.diary.speech.entity.Transcription;
import alex.drankov.diary.speech.entity.TranscriptionStatus;
import alex.drankov.diary.speech.mapper.TranscriptionMapper;
import alex.drankov.diary.speech.repository.TranscriptionRepository;
import alex.drankov.diary.speech.service.model.TranscriptionIdentity;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptionServiceTest {
    private final TranscriptionRepository tasks = mock(TranscriptionRepository.class);
    private final UserAccess access = mock(UserAccess.class);
    private final SpeechConsent consent = mock(SpeechConsent.class);
    private final Idempotency idempotency = mock(Idempotency.class);
    private final SpeechAdapter model = mock(SpeechAdapter.class);
    private final UUID user = UUID.randomUUID();
    private final TranscriptionService service = service(TestFixtures.CLOCK);

    private TranscriptionService service(Clock clock) {
        return new TranscriptionService(tasks, access, consent, new WavValidator(),
                Mappers.getMapper(TranscriptionMapper.class), idempotency, model, clock);
    }

    private void mutations() {
        when(idempotency.execute(eq(user), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
        when(tasks.saveAndFlush(any())).thenAnswer(call -> TestFixtures.persisted(call.getArgument(0)));
        when(model.available()).thenReturn(true);
        when(model.modelId()).thenReturn("speech-test");
    }

    @Test
    @DisplayName("Загрузка фиксирует хеш, модель, длительность и 15 минут хранения; исходный массив не хранится по ссылке")
    void create() {
        mutations();
        byte[] audio = WavFixtures.wav(16000);
        var result = service.create(user, UUID.randomUUID(), audio);
        assertThat(result.status()).isEqualTo(202);
        assertThat(result.body().durationMillis()).isEqualTo(1000);
        assertThat(result.body().modelId()).isEqualTo("speech-test");
        assertThat(result.body().expiresAt()).isEqualTo(TestFixtures.NOW.plusSeconds(900));
        assertThat(result.body().transcript()).isNull();
        verify(access).lock(user);
        verify(consent).require(user);
    }

    @Test
    @DisplayName("Без адаптера, при активной задаче и после 30 загрузок за час новая задача не создаётся")
    void limits() {
        mutations();
        when(model.available()).thenReturn(false);
        assertCode("SPEECH_MODEL_NOT_CONFIGURED");
        when(model.available()).thenReturn(true);
        when(tasks.existsByUserIdAndStatusIn(eq(user), any())).thenReturn(true);
        assertCode("TRANSCRIPTION_ALREADY_ACTIVE");
        when(tasks.existsByUserIdAndStatusIn(eq(user), any())).thenReturn(false);
        when(tasks.countByUserIdAndCreatedAtAfter(eq(user), any())).thenReturn(30L);
        assertCode("SPEECH_RATE_LIMIT");
    }

    @Test
    @DisplayName("Чужой идентификатор даёт 404; просроченная расшифровка даёт 410 и не возвращает текст")
    void ownershipAndExpiry() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(user, id)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(404));
        var task = task();
        task.succeed("Синтетическая заметка", TestFixtures.NOW);
        when(tasks.findByIdAndUserId(task.getId(), user)).thenReturn(Optional.of(task));
        assertThat(service.get(user, task.getId()).transcript()).isEqualTo("Синтетическая заметка");
        var late = service(Clock.fixed(TestFixtures.NOW.plusSeconds(86400), ZoneOffset.UTC));
        assertThatThrownBy(() -> late.get(user, task.getId())).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(410));
    }

    @Test
    @DisplayName("Отмена повторяема, удаление стирает квитанцию; завершённая задача не отменяется")
    void cancelAndDelete() {
        var task = task();
        when(tasks.findByIdAndUserId(task.getId(), user)).thenReturn(Optional.of(task));
        service.cancel(user, task.getId());
        service.cancel(user, task.getId());
        assertThat(task.getAudio()).isNull();
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.CANCELLED);
        task.succeed("Текст", TestFixtures.NOW);
        assertThatThrownBy(() -> service.cancel(user, task.getId())).isInstanceOf(ApiException.class);
        service.delete(user, task.getId());
        verify(idempotency).erase(user, task.getId());
        verify(tasks).delete(task);
    }

    @Test
    @DisplayName("Очистка стирает результат и квитанцию; через семь дней удаляет метаданные")
    void cleanup() {
        var task = task();
        when(tasks.findByIdAndUserId(task.getId(), user)).thenReturn(Optional.of(task));
        var identity = new TranscriptionIdentity(task.getId(), user);
        service.expire(identity);
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.QUEUED);
        var late = service(Clock.fixed(TestFixtures.NOW.plusSeconds(900), ZoneOffset.UTC));
        late.expire(identity);
        assertThat(task.getStatus()).isEqualTo(TranscriptionStatus.EXPIRED);
        assertThat(task.getAudio()).isNull();
        service(Clock.fixed(TestFixtures.NOW.plusSeconds(900 + 7 * 86400), ZoneOffset.UTC)).expire(identity);
        verify(tasks).delete(task);
    }

    private Transcription task() {
        return TestFixtures.persisted(new Transcription(user, WavFixtures.wav(16000), "a".repeat(64), 1000,
                "speech-test", TestFixtures.NOW.plusSeconds(900)));
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> service.create(user, UUID.randomUUID(), WavFixtures.wav(16000)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
