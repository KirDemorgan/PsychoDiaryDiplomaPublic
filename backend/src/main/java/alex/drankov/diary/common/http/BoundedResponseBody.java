package alex.drankov.diary.common.http;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public final class BoundedResponseBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;
    private final int limit;

    public BoundedResponseBody(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Invalid response limit");
        }
        this.limit = limit;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
        for (var item : items) {
            if (item.remaining() > limit - bytes.size()) {
                subscription.cancel();
                result.completeExceptionally(new IllegalArgumentException("Model response exceeds limit"));
                return;
            }
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable exception) {
        result.completeExceptionally(exception);
    }

    @Override
    public void onComplete() {
        result.complete(bytes.toByteArray());
    }
}
