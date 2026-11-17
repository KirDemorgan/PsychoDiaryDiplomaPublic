package alex.drankov.diary.common.idempotency.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.entity.MutationReceipt;
import alex.drankov.diary.common.idempotency.repository.ReceiptRepository;

import java.util.UUID;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class Idempotency {
    private final ReceiptRepository receipts;
    private final JsonMapper json;

    /**
     * Replays a stored mutation result or executes and records a new mutation atomically.
     *
     * <p>The caller must hold the owner's row lock in the same transaction. This prevents concurrent
     * requests with one operation key from both executing the mutation.
     *
     * @param <T>      the response body type
     * @param user     the resource owner
     * @param route    the operation scope
     * @param key      the client-generated operation identifier
     * @param request  the payload used to detect conflicting retries
     * @param type     the response type used to deserialize a replay
     * @param mutation the operation to execute when no receipt exists
     * @return the original or newly created mutation result
     * @throws ApiException if the key is missing, the payload conflicts, or the result was deleted
     */
    public <T> MutationResult<T> execute(
            UUID user,
            String route,
            UUID key,
            Object request,
            Class<T> type,
            Supplier<MutationResult<T>> mutation
    ) {
        if (key == null) {
            throw ApiException.invalid("Передайте Idempotency-Key UUID");
        }
        String hash = Hashing.sha256(json.writeValueAsString(request));
        var old = receipts.findByUserIdAndRouteAndOperationId(user, route, key);
        if (old.isPresent()) {
            var value = old.get();
            if (value.isGone()) {
                throw new ApiException(410, "RESOURCE_GONE", "Результат операции удалён");
            }
            if (!hash.equals(value.getRequestHash())) {
                throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "Ключ уже использован с другим запросом");
            }
            return new MutationResult<>(json.readValue(value.getResponsePayload(), type),
                    value.getResourceId(), value.getResourceVersion(), value.getResponseStatus());
        }
        var result = mutation.get();
        receipts.save(
                new MutationReceipt(user, route, key, hash, result.resourceId(), result.version(), result.status(),
                        json.writeValueAsString(result.body())));
        return result;
    }

    public void erase(UUID user, UUID resource) {
        receipts.findByUserIdAndResourceId(user, resource).forEach(MutationReceipt::erase);
    }
}
