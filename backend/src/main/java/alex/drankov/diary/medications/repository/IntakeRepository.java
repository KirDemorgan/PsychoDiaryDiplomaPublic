package alex.drankov.diary.medications.repository;

import alex.drankov.diary.medications.entity.IntakeEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntakeRepository extends JpaRepository<IntakeEvent, UUID> {
    Optional<IntakeEvent> findByIdAndUserId(UUID id, UUID userId);

    Optional<IntakeEvent> findByScheduleSlotIdAndPlannedAt(UUID slotId, Instant at);

    List<IntakeEvent> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    List<IntakeEvent> findByUserIdAndMedicationId(UUID userId, UUID medicationId);

    @Query("select i from IntakeEvent i where i.userId=:user and coalesce(i.plannedAt,i.actualAt)>=:from and coalesce(i.plannedAt,i.actualAt)<:until and (:medication is null or i.medicationId=:medication)")
    Page<IntakeEvent> list(UUID user, Instant from, Instant until, UUID medication, Pageable page);
}
