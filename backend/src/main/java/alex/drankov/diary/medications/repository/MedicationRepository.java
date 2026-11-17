package alex.drankov.diary.medications.repository;

import alex.drankov.diary.medications.entity.Medication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    Optional<Medication> findByIdAndUserId(UUID id, UUID userId);

    @Query("select m from Medication m where m.userId=:user and ((:archived=true and m.archivedAt is not null) or (:archived=false and m.archivedAt is null))")
    Page<Medication> list(UUID user, boolean archived, Pageable page);

    List<Medication> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);
}
