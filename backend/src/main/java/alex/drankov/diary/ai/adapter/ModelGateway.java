package alex.drankov.diary.ai.adapter;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModelGateway {
    private final ModelAdapter model;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(1);

    @Value("${app.ai.timeout:PT30S}")
    private Duration timeout;

    public String generate(ModelRequest request) throws Exception {
        var future = calls.submit(() -> {
            if (!capacity.tryAcquire()) {
                throw new IllegalStateException("Model call capacity exhausted");
            }
            try {
                return model.generate(request);
            } finally {
                capacity.release();
            }
        });
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw new IllegalStateException("Model call failed", exception);
        } finally {
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
