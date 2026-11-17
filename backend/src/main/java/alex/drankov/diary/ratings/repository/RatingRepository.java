package alex.drankov.diary.ratings.repository;

import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<StateRating, UUID> {
    List<StateRating> findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(UUID userId, LocalDate from, LocalDate to);

    List<StateRating> findByUserIdOrderByLocalDateAscSlotAsc(UUID userId);

    Optional<StateRating> findByUserIdAndLocalDateAndSlot(UUID userId, LocalDate date, RatingSlot slot);

    @Query("select r from StateRating r where r.userId=:user and r.localDate between :from and :to and (:slot is null or r.slot=:slot)")
    Page<StateRating> list(UUID user, LocalDate from, LocalDate to, RatingSlot slot, Pageable pageable);
}
