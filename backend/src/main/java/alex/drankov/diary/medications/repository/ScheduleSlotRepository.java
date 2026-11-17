package alex.drankov.diary.medications.repository;

import alex.drankov.diary.medications.entity.ScheduleSlot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, UUID> {
    List<ScheduleSlot> findByScheduleVersionIdOrderByLocalTimeAscIdAsc(UUID versionId);

    @Query("select s from ScheduleSlot s, Medication m where s.medicationId=m.id and m.userId=:user order by s.localTime, s.id")
    List<ScheduleSlot> forUser(UUID user);
}
