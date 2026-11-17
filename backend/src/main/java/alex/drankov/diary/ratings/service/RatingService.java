package alex.drankov.diary.ratings.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.ratings.dto.PutRatingRequest;
import alex.drankov.diary.ratings.dto.RatingResponse;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {
    private final RatingRepository ratings;
    private final RatingMapper mapper;
    private final UserAccess access;
    private final Idempotency idempotency;

    public RatingResponse get(UUID user, LocalDate date, RatingSlot slot) {
        return mapper.response(find(user, date, slot));
    }

    public PageResponse<RatingResponse> list(
            UUID user,
            LocalDate from,
            LocalDate to,
            RatingSlot slot,
            int page,
            int size
    ) {
        TimeRules.range(from, to, 366);
        return PageResponse.of(ratings.list(user, from, to, slot, PageRequest.of(page, size,
                        Sort.by(Sort.Order.desc("localDate"), Sort.Order.asc("slot"), Sort.Order.desc("id")))),
                mapper::response);
    }

    @Transactional
    public MutationResult<RatingResponse> put(
            UUID user,
            LocalDate date,
            RatingSlot slot,
            UUID key,
            String ifMatch,
            PutRatingRequest request
    ) {
        var owner = access.lock(user);
        String route = "PUT /ratings/" + date + "/" + slot;
        return idempotency.execute(user, route, key, request, RatingResponse.class, () -> {
            TimeRules.zone(request.timeZone());
            validateValues(request);
            var existing = ratings.findByUserIdAndLocalDateAndSlot(user, date, slot);
            existing.ifPresent(r -> Versions.require(ifMatch, r.getVersion()));
            var rating = existing.orElseGet(() -> new StateRating(user, date, slot));
            rating.replace(request.anxiety(), request.mood(), request.energy(), request.timeZone());
            ratings.saveAndFlush(rating);
            owner.advanceDataRevision();
            return new MutationResult<>(mapper.response(rating), rating.getId(), rating.getVersion(),
                    existing.isPresent() ? 200 : 201);
        });
    }

    @Transactional
    public void delete(
            UUID user,
            LocalDate date,
            RatingSlot slot,
            String ifMatch
    ) {
        var owner = access.lock(user);
        var rating = find(user, date, slot);
        Versions.require(ifMatch, rating.getVersion());
        idempotency.erase(user, rating.getId());
        ratings.delete(rating);
        owner.advanceDataRevision();
    }

    private StateRating find(UUID user, LocalDate date, RatingSlot slot) {
        return ratings.findByUserIdAndLocalDateAndSlot(user, date, slot).orElseThrow(ApiException::notFound);
    }

    public static void validateValues(PutRatingRequest request) {
        if (request.anxiety() == null && request.mood() == null && request.energy() == null) {
            throw ApiException.invalid("Пустую оценку удалите через DELETE");
        }
        for (Integer value : new Integer[]{request.anxiety(), request.mood(), request.energy()}) {
            if (value != null && (value < 0 || value > 10)) {
                throw ApiException.invalid("Оценка должна быть от 0 до 10");
            }
        }
    }
}
