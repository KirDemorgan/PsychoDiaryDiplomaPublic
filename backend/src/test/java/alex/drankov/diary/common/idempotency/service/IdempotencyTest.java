package alex.drankov.diary.common.idempotency.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.entity.MutationReceipt;
import alex.drankov.diary.common.idempotency.repository.ReceiptRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyTest {
    @Mock
    ReceiptRepository receipts;
    JsonMapper json = new JsonMapper();
    Idempotency service;
    UUID user = UUID.randomUUID(), key = UUID.randomUUID(), resource = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new Idempotency(receipts, json);
    }

    record Value(String text) {
    }

    @Test
    @DisplayName("Повтор операции возвращает сохранённый результат без повторного изменения данных")
    void savesFirstResultAndDoesNotRunMutationOnReplay() {
        var request = new Value("request");
        var body = new Value("original");
        when(receipts.findByUserIdAndRouteAndOperationId(user, "POST /entries", key)).thenReturn(Optional.empty());
        var result = service.execute(user, "POST /entries", key, request, Value.class, () -> new MutationResult<>(body, resource, 0, 201));
        assertThat(result.body()).isEqualTo(body);
        var captor = ArgumentCaptor.forClass(MutationReceipt.class);
        verify(receipts).save(captor.capture());
        when(receipts.findByUserIdAndRouteAndOperationId(user, "POST /entries", key)).thenReturn(Optional.of(captor.getValue()));
        var replay = service.execute(user, "POST /entries", key, request, Value.class, () -> {
            throw new AssertionError("must not mutate");
        });
        assertThat(replay).isEqualTo(result);
        assertThatThrownBy(() -> service.execute(user, "POST /entries", key, new Value("different"), Value.class, () -> result))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    @DisplayName("Удаление очищает чувствительный ответ и запрещает повторное получение результата")
    void deletedResultsEraseSensitivePayloadAndCannotBeReplayed() {
        var receipt = new MutationReceipt(user, "route", key, Hashing.sha256("request"), resource, 0, 201, "private text");
        when(receipts.findByUserIdAndResourceId(user, resource)).thenReturn(List.of(receipt));
        service.erase(user, resource);
        assertThat(receipt.getResponsePayload()).isNull();
        assertThat(receipt.isGone()).isTrue();
        when(receipts.findByUserIdAndRouteAndOperationId(user, "route", key)).thenReturn(Optional.of(receipt));
        assertThatThrownBy(() -> service.execute(user, "route", key, "different", Value.class, () -> null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(410));
    }

    @Test
    @DisplayName("Операция без ключа идемпотентности отклоняется до изменения данных")
    void missingKeyFailsBeforeMutation() {
        assertThatThrownBy(() -> service.execute(user, "route", null, "request", Value.class, () -> null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(receipts);
    }
}
