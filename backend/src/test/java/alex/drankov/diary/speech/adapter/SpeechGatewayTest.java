package alex.drankov.diary.speech.adapter;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeechGatewayTest {
    @Test
    @DisplayName("Gateway передаёт отдельную копию и очищает её после успешного ASR")
    void success() throws Exception {
        var model = mock(SpeechAdapter.class);
        var gateway = new SpeechGateway(model);
        ReflectionTestUtils.setField(gateway, "timeout", Duration.ofSeconds(1));
        byte[] original = new byte[]{1, 2};
        byte[][] captured = new byte[1][];
        when(model.transcribe(any())).thenAnswer(call -> {
            captured[0] = call.getArgument(0);
            assertThat(captured[0]).isNotSameAs(original).containsExactly(1, 2);
            return "Текст";
        });
        try {
            assertThat(gateway.transcribe(original)).isEqualTo("Текст");
            assertThat(captured[0]).containsOnly((byte) 0);
            assertThat(original).containsExactly(1, 2);
        } finally {
            gateway.shutdown();
        }
    }

    @Test
    @DisplayName("Долгий ASR ограничен timeout и получает interrupt; ресурс освобождается")
    void timeout() throws Exception {
        var model = mock(SpeechAdapter.class);
        var gateway = new SpeechGateway(model);
        var interrupted = new CountDownLatch(1);
        ReflectionTestUtils.setField(gateway, "timeout", Duration.ofMillis(100));
        when(model.transcribe(any())).thenAnswer(call -> {
            try {
                new CountDownLatch(1).await();
                return "unreachable";
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
        });
        try {
            assertThatThrownBy(() -> gateway.transcribe(new byte[]{1})).isInstanceOf(TimeoutException.class);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            gateway.shutdown();
        }
    }
}
