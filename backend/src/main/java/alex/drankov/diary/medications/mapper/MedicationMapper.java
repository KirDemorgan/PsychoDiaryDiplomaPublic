package alex.drankov.diary.medications.mapper;

import alex.drankov.diary.medications.dto.IntakeResponse;
import alex.drankov.diary.medications.dto.ScheduleSlotResponse;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.ScheduleSlot;

import org.springframework.stereotype.Component;

@Component
public class MedicationMapper {
    public IntakeResponse intake(IntakeEvent value) {
        return new IntakeResponse(value.getId(), value.getMedicationId(), value.getScheduleSlotId(),
                value.getPlannedAt(), value.getKind(), value.getStatus(), value.getActualAt(), value.getActualDose(),
                value.getUnit(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }

    public ScheduleSlotResponse slot(ScheduleSlot value) {
        return new ScheduleSlotResponse(value.getId(), value.getLocalTime(), value.daysOfWeek(),
                value.getDose(), value.getUnit());
    }
}
