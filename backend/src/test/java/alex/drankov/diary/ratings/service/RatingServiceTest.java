package alex.drankov.diary.ratings.service;

import org.springframework.data.domain.PageImpl;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.ratings.dto.PutRatingRequest;
import alex.drankov.diary.ratings.dto.RatingResponse;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {
    @Mock
    RatingRepository ratings;
    @Mock
    UserAccess access;
    @Mock
    Idempotency idempotency;
    RatingService service;
    User owner;
    LocalDate date = LocalDate.of(2026, 10, 13);

    @BeforeEach
    void setup() {
        owner = user();
        service = new RatingService(ratings, Mappers.getMapper(RatingMapper.class), access, idempotency);
    }

    private void mutation() {
        when(access.lock(owner.getId())).thenReturn(owner);
        when(idempotency.execute(eq(owner.getId()), anyString(), any(), any(), eq(RatingResponse.class), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
    }

    @Test
    @DisplayName("Частичная оценка сохраняет ноль и незаполненные показатели")
    void createsPartialRatingAndPreservesZero() {
        mutation();
        when(ratings.findByUserIdAndLocalDateAndSlot(owner.getId(), date, RatingSlot.MORNING)).thenReturn(Optional.empty());
        when(ratings.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        var value = service.put(owner.getId(), date, RatingSlot.MORNING, UUID.randomUUID(), null, new PutRatingRequest(0, null, 10, "UTC"));
        assertThat(value.status()).isEqualTo(201);
        assertThat(value.body().mood()).isNull();
        assertThat(value.body().anxiety()).isZero();
        assertThat(owner.getDataRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Обновление оценки требует актуальной версии и сохраняет тот же ресурс")
    void existingRatingRequiresVersionAndUpdatesInPlace() {
        mutation();
        var rating = persisted(new StateRating(owner.getId(), date, RatingSlot.DAY));
        when(ratings.findByUserIdAndLocalDateAndSlot(owner.getId(), date, RatingSlot.DAY)).thenReturn(Optional.of(rating));
        var request = new PutRatingRequest(2, 3, null, "UTC");
        assertThatThrownBy(() -> service.put(owner.getId(), date, RatingSlot.DAY, UUID.randomUUID(), null, request)).isInstanceOf(ApiException.class);
        when(ratings.saveAndFlush(rating)).thenReturn(rating);
        var value = service.put(owner.getId(), date, RatingSlot.DAY, UUID.randomUUID(), "\"v0\"", request);
        assertThat(value.status()).isEqualTo(200);
        assertThat(value.resourceId()).isEqualTo(rating.getId());
    }

    @Test
    @DisplayName("Чтение, список и удаление оценок ограничены владельцем")
    void readsListsDeletesOnlyOwnerResources() {
        var rating = persisted(new StateRating(owner.getId(), date, RatingSlot.DAY));
        rating.replace(1, null, null, "UTC");
        when(ratings.findByUserIdAndLocalDateAndSlot(owner.getId(), date, RatingSlot.DAY)).thenReturn(Optional.of(rating));
        assertThat(service.get(owner.getId(), date, RatingSlot.DAY).anxiety()).isEqualTo(1);
        when(ratings.list(eq(owner.getId()), eq(date), eq(date), isNull(), any())).thenReturn(new PageImpl<>(List.of(rating)));
        assertThat(service.list(owner.getId(), date, date, null, 0, 20).items()).hasSize(1);
        when(access.lock(owner.getId())).thenReturn(owner);
        service.delete(owner.getId(), date, RatingSlot.DAY, "\"v0\"");
        verify(idempotency).erase(owner.getId(), rating.getId());
        verify(ratings).delete(rating);
        UUID other = UUID.randomUUID();
        when(ratings.findByUserIdAndLocalDateAndSlot(other, date, RatingSlot.DAY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(other, date, RatingSlot.DAY)).isInstanceOf(ApiException.class);
    }
}
