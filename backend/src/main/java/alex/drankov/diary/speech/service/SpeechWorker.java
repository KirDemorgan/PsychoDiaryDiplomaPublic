package alex.drankov.diary.speech.service;

import alex.drankov.diary.common.api.Texts;
import alex.drankov.diary.speech.adapter.SpeechAdapter;
import alex.drankov.diary.speech.adapter.SpeechGateway;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.speech.worker-enabled", havingValue = "true", matchIfMissing = true)
public class SpeechWorker {
    private final SpeechQueue queue;
    private final SpeechAdapter model;
    private final SpeechGateway gateway;

    @Scheduled(fixedDelayString = "${app.speech.poll-delay:PT1S}")
    public void tick() {
        var work = queue.claim();
        if (work == null) {
            return;
        }
        try {
            if (!model.available()) {
                queue.fail(work, "SPEECH_MODEL_NOT_CONFIGURED");
                return;
            }
            if (!Objects.equals(work.modelId(), model.modelId())) {
                queue.fail(work, "MODEL_CHANGED");
                return;
            }
            String text = null;
            String error = null;
            try {
                text = gateway.transcribe(work.audio());
                if (text == null || text.isBlank()) {
                    error = "NO_SPEECH";
                } else if (Texts.exceeds(text, 5000) || text.codePoints().anyMatch(value ->
                        value >= 0xD800 && value <= 0xDFFF || Character.isISOControl(value) && value != '\n' && value != '\t')) {
                    error = "INVALID_SPEECH_RESULT";
                }
            } catch (TimeoutException exception) {
                error = "SPEECH_TIMEOUT";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                error = "SPEECH_INTERRUPTED";
            } catch (Exception exception) {
                error = "SPEECH_SERVICE_ERROR";
            }
            if (error != null) {
                queue.fail(work, error);
            } else {
                queue.succeed(work, text.strip());
            }
        } finally {
            Arrays.fill(work.audio(), (byte) 0);
        }
    }
}
