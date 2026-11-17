package alex.drankov.diary.common.http;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BoundedResponseBodyTest {
    @Test
    @DisplayName("Лимит применяется ко всем сетевым чанкам вместе; превышение отменяет подписку")
    void limit() {
        var subscription = mock(Flow.Subscription.class);
        var body = new BoundedResponseBody(3);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2})));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{3, 4})));
        verify(subscription).cancel();
        assertThatThrownBy(() -> body.getBody().toCompletableFuture().join()).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Точная граница лимита допускается; ошибка сети передаётся без неполного успешного результата")
    void completion() {
        var body = new BoundedResponseBody(2);
        body.onSubscribe(mock(Flow.Subscription.class));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1}), ByteBuffer.wrap(new byte[]{2})));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().join()).containsExactly(1, 2);
        var failed = new BoundedResponseBody(2);
        failed.onError(new IllegalStateException("failure"));
        assertThatThrownBy(() -> failed.getBody().toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BoundedResponseBody(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
