package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.medications.entity.ScheduleSlot;
import alex.drankov.diary.medications.entity.ScheduleVersion;
import alex.drankov.diary.medications.mapper.MedicationMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OccurrenceCalculator {
    private final MedicationMapper mapper;

    public List<OccurrenceResponse> calculate(
            Medication medication,
            ScheduleVersion version,
            List<ScheduleSlot> slots,
            List<IntakeEvent> intakes,
            Instant from,
            Instant until
    ) {
        ZoneId zone = ZoneId.of(version.getTimeZone());
        LocalDate first = from.atZone(zone).toLocalDate();
        LocalDate last = until.minusNanos(1).atZone(zone).toLocalDate();
        var result = new ArrayList<OccurrenceResponse>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            for (var slot : slots) {
                Instant at = plannedAt(day, slot, zone);
                if (!valid(medication, version, slot, day, at) || at.isBefore(from) || !at.isBefore(until)) {
                    continue;
                }
                var marked = intakes.stream().filter(i -> slot.getId().equals(i.getScheduleSlotId())
                        && at.equals(i.getPlannedAt())).findFirst();
                result.add(new OccurrenceResponse(id(slot.getId(), at), medication.getId(), medication.getName(),
                        version.getId(), slot.getId(), at, slot.getDose(), slot.getUnit(), version.getTimeZone(),
                        marked.map(IntakeEvent::getStatus).orElse(IntakeStatus.NO_DATA),
                        marked.map(mapper::intake).orElse(null)));
            }
        }
        result.sort(Comparator.comparing(OccurrenceResponse::plannedAt).thenComparing(OccurrenceResponse::occurrenceId));
        return List.copyOf(result);
    }

    public static Instant plannedAt(
            LocalDate day,
            ScheduleSlot slot,
            ZoneId zone
    ) {
        var local = day.atTime(slot.getLocalTime());
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            return zone.getRules().getTransition(local).getInstant();
        }
        return local.toInstant(offsets.getFirst());
    }

    public static boolean valid(
            Medication medication,
            ScheduleVersion version,
            ScheduleSlot slot,
            LocalDate day,
            Instant at
    ) {
        return slot.daysOfWeek().contains(day.getDayOfWeek()) && !at.isBefore(version.getEffectiveFrom())
                && (version.getEffectiveUntil() == null || at.isBefore(version.getEffectiveUntil()))
                && (version.getTreatmentEndsOn() == null || !day.isAfter(version.getTreatmentEndsOn()))
                && (medication.getArchivedAt() == null || at.isBefore(medication.getArchivedAt()));
    }

    public static String id(
            UUID slot,
            Instant at
    ) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((slot + "@" + at).getBytes(StandardCharsets.UTF_8));
    }

    public static String[] decode(String id) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8).split("@", -1);
            if (parts.length != 2 || !id(UUID.fromString(parts[0]), Instant.parse(parts[1])).equals(id)) {
                throw ApiException.notFound();
            }
            return parts;
        } catch (IllegalArgumentException exception) {
            throw ApiException.notFound();
        }
    }
}
