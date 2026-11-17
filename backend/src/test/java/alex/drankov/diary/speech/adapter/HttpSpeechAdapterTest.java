package alex.drankov.diary.speech.adapter;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpSpeechAdapterTest {
    @Test
    @DisplayName("Незаполненная конфигурация отключает ASR; неполная конфигурация и небезопасный URI отклоняются")
    void configuration() {
        var missing = new HttpSpeechAdapter("", "", "", Duration.ofSeconds(1));
        assertThat(missing.available()).isFalse();
        assertThat(missing.modelId()).isNull();
        assertThatThrownBy(() -> missing.transcribe(new byte[0])).isInstanceOf(IllegalStateException.class);
        for (String endpoint : new String[]{"file:///tmp/asr", "https://name:secret@example.test/asr",
                "https://example.test/asr?token=secret", "https://example.test/asr#x", "http:///asr"}) {
            assertThatThrownBy(() -> new HttpSpeechAdapter(endpoint, "test", "", Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new HttpSpeechAdapter("http://localhost/asr", "", "", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpSpeechAdapter("", "", "", Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HTTP адаптер передаёт WAV и выбранную ASR модель; проверяет JSON, модель, статус и размер ответа")
    void transport() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var response = new AtomicReference<>("{\"text\":\"Синтетический текст\",\"modelId\":\"speech-test\"}");
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        var received = new AtomicReference<byte[]>();
        server.createContext("/transcribe", exchange -> {
            received.set(exchange.getRequestBody().readAllBytes());
            assertThat(exchange.getRequestHeaders().getFirst("X-Speech-Model")).isEqualTo("speech-test");
            assertThat(exchange.getRequestHeaders().getFirst("X-Speech-Language")).isEqualTo("ru");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        try {
            var adapter = new HttpSpeechAdapter("http://127.0.0.1:" + server.getAddress().getPort() + "/transcribe",
                    "speech-test", "test-token", Duration.ofSeconds(2));
            assertThat(adapter.available()).isTrue();
            assertThat(adapter.transcribe(new byte[]{1, 2})).isEqualTo("Синтетический текст");
            assertThat(received.get()).containsExactly(1, 2);
            for (String invalid : new String[]{"{}", "{\"text\":1,\"modelId\":\"speech-test\"}",
                    "{\"text\":\"x\",\"modelId\":\"other\"}",
                    "{\"text\":\"x\",\"text\":\"y\",\"modelId\":\"speech-test\"}",
                    "{\"text\":\"x\",\"modelId\":\"speech-test\"} {}", "x".repeat(65537)}) {
                response.set(invalid);
                assertThatThrownBy(() -> adapter.transcribe(new byte[]{1})).isInstanceOf(Exception.class);
            }
            response.set("{}");
            status.set(503);
            assertThatThrownBy(() -> adapter.transcribe(new byte[]{1})).isInstanceOf(IllegalStateException.class);
        } finally {
            server.stop(0);
        }
    }
}
