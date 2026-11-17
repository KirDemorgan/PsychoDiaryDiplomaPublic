package alex.drankov.diary.medications.repository;

import alex.drankov.diary.medications.entity.ScheduleVersion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, UUID> {
    List<ScheduleVersion> findByMedicationIdOrderByEffectiveFromAsc(UUID medicationId);

    @Query("select v from ScheduleVersion v, Medication m where v.medicationId=m.id and m.userId=:user order by v.effectiveFrom, v.id")
    List<ScheduleVersion> forUser(UUID user);
}
