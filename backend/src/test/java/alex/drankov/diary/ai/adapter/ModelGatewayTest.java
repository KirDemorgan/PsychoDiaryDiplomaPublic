package alex.drankov.diary.ai.adapter;

import alex.drankov.diary.ai.entity.AiKind;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelGatewayTest {
    @Test
    @DisplayName("Gateway ограничивает время вызова, отменяет ожидание и закрывает executor")
    void timeoutAndShutdown() throws Exception {
        var model = mock(ModelAdapter.class);
        when(model.generate(any())).thenAnswer(call -> {
            Thread.sleep(1000);
            return "late";
        });
        var gateway = new ModelGateway(model);
        ReflectionTestUtils.setField(gateway, "timeout", Duration.ofMillis(20));
        try {
            assertThatThrownBy(() -> gateway.generate(new ModelRequest(AiKind.ENTRY, "system", "data", "v1")))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            gateway.shutdown();
        }
    }

    @Test
    @DisplayName("Gateway возвращает ответ адаптера, не пишет содержимое ModelRequest в toString")
    void response() throws Exception {
        var model = mock(ModelAdapter.class);
        when(model.generate(any())).thenReturn("{\"candidates\":[]}");
        var gateway = new ModelGateway(model);
        ReflectionTestUtils.setField(gateway, "timeout", Duration.ofSeconds(1));
        try {
            var request = new ModelRequest(AiKind.ENTRY, "system", "synthetic-private-marker", "v1");
            assertThat(request.toString()).doesNotContain("synthetic-private-marker");
            assertThat(gateway.generate(request)).contains("candidates");
        } finally {
            gateway.shutdown();
        }
    }
    @Test
    @DisplayName("Ошибка сети проходит через gateway как IOException для политики retry и fallback")
    void transportFailure() throws Exception {
        var model = mock(ModelAdapter.class);
        when(model.generate(any())).thenThrow(new java.io.IOException("network failure"));
        var gateway = new ModelGateway(model);
        ReflectionTestUtils.setField(gateway, "timeout", Duration.ofSeconds(1));
        try {
            assertThatThrownBy(() -> gateway.generate(new ModelRequest(AiKind.ENTRY, "system", "data", "v1")))
                    .isInstanceOf(java.io.IOException.class);
        } finally {
            gateway.shutdown();
        }
    }

}
