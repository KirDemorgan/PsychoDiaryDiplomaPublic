package alex.drankov.diary.speech.service;

import alex.drankov.diary.speech.adapter.SpeechAdapter;
import alex.drankov.diary.speech.adapter.SpeechGateway;
import alex.drankov.diary.speech.service.model.SpeechWorkItem;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechWorkerTest {
    private final SpeechQueue queue = mock(SpeechQueue.class);
    private final SpeechAdapter model = mock(SpeechAdapter.class);
    private final SpeechGateway gateway = mock(SpeechGateway.class);
    private final SpeechWorker worker = new SpeechWorker(queue, model, gateway);
    private final SpeechWorkItem work = new SpeechWorkItem(UUID.randomUUID(), UUID.randomUUID(), new byte[]{1, 2}, "speech-test");

    private void setup() {
        when(queue.claim()).thenReturn(work);
        when(model.available()).thenReturn(true);
        when(model.modelId()).thenReturn("speech-test");
    }

    @Test
    @DisplayName("Расшифровка возвращает текст для редактирования и стирает рабочую копию аудио")
    void success() throws Exception {
        setup();
        when(gateway.transcribe(any())).thenReturn("  Синтетическая заметка  ");
        worker.tick();
        verify(queue).succeed(work, "Синтетическая заметка");
        assertThat(work.audio()).containsOnly((byte) 0);
        assertThat(work.toString()).contains("<redacted>");
    }

    @Test
    @DisplayName("Тишина, слишком длинный текст, управляющие символы и одиночный surrogate не сохраняются")
    void invalidResult() throws Exception {
        setup();
        when(gateway.transcribe(any())).thenReturn("  ");
        worker.tick();
        verify(queue).fail(work, "NO_SPEECH");
        for (String invalid : new String[]{"а".repeat(5001), "текст\u0000", "\uD800"}) {
            when(gateway.transcribe(any())).thenReturn(invalid);
            worker.tick();
        }
        verify(queue, org.mockito.Mockito.times(3)).fail(work, "INVALID_SPEECH_RESULT");
        verify(queue, never()).succeed(any(), any());
    }

    @Test
    @DisplayName("Смена модели и отсутствие адаптера завершают задачу без вызова ASR")
    void unavailable() throws Exception {
        setup();
        when(model.available()).thenReturn(false);
        worker.tick();
        verify(queue).fail(work, "SPEECH_MODEL_NOT_CONFIGURED");
        when(model.available()).thenReturn(true);
        when(model.modelId()).thenReturn("other");
        worker.tick();
        verify(queue).fail(work, "MODEL_CHANGED");
        verify(gateway, never()).transcribe(any());
    }

    @Test
    @DisplayName("Timeout и ошибка транспорта получают безопасные коды без текста исключений")
    void failures() throws Exception {
        setup();
        when(gateway.transcribe(any())).thenThrow(new TimeoutException());
        worker.tick();
        verify(queue).fail(work, "SPEECH_TIMEOUT");
        org.mockito.Mockito.doThrow(new IllegalStateException("sensitive response")).when(gateway).transcribe(any());
        worker.tick();
        verify(queue).fail(work, "SPEECH_SERVICE_ERROR");
    }

    @Test
    @DisplayName("Сбой публикации не маскируется как ошибка ASR; lease восстанавливается очередью")
    void publishingFailure() throws Exception {
        setup();
        when(gateway.transcribe(any())).thenReturn("Текст");
        org.mockito.Mockito.doThrow(new IllegalStateException("database failure")).when(queue).succeed(work, "Текст");
        org.assertj.core.api.Assertions.assertThatThrownBy(worker::tick).isInstanceOf(IllegalStateException.class);
        verify(queue, never()).fail(any(), any());
    }
}
