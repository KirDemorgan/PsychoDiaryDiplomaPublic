package alex.drankov.diary.speech.adapter;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeechGateway {
    private final SpeechAdapter model;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(1);

    @Value("${app.speech.timeout:PT60S}")
    private Duration timeout;

    public String transcribe(byte[] audio) throws Exception {
        byte[] input = audio.clone();
        var future = calls.submit(() -> {
            if (!capacity.tryAcquire()) {
                throw new IllegalStateException("Speech capacity exhausted");
            }
            try {
                return model.transcribe(input);
            } finally {
                Arrays.fill(input, (byte) 0);
                capacity.release();
            }
        });
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } finally {
            Arrays.fill(input, (byte) 0);
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        calls.shutdownNow();
    }
}
