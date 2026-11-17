package alex.drankov.diary.analytics.service;

import alex.drankov.diary.analytics.dto.CalendarDayResponse;
import alex.drankov.diary.analytics.dto.CalendarResponse;
import alex.drankov.diary.analytics.dto.ComparisonResponse;
import alex.drankov.diary.analytics.dto.ContextTagResponse;
import alex.drankov.diary.analytics.dto.DayResponse;
import alex.drankov.diary.analytics.dto.MedicationDayResponse;
import alex.drankov.diary.analytics.dto.OverviewResponse;
import alex.drankov.diary.analytics.dto.PeriodResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.service.IntakeService;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.mapper.RatingMapper;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.mapper.TagMapper;
import alex.drankov.diary.tags.repository.EntryTagRepository;
import alex.drankov.diary.tags.service.TagQuery;
import alex.drankov.diary.users.service.UserAccess;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsService {
    private final RatingRepository ratings;
    private final EntryRepository entries;
    private final EntryTagRepository tags;
    private final MedicationRepository medications;
    private final IntakeRepository intakes;
    private final IntakeService intakeService;
    private final UserAccess access;
    private final AnalyticsCalculator calculator;
    private final RatingMapper ratingMapper;
    private final EntryMapper entryMapper;
    private final TagQuery tagQuery;
    private final TagMapper tagMapper;

    public OverviewResponse overview(
            UUID user,
            LocalDate from,
            LocalDate to,
            String timeZone,
            int threshold
    ) {
        var owner = access.read(user);
        var period = period(from, to, timeZone == null ? owner.getTimeZone() : timeZone);
        if (threshold < 0 || threshold > 10) {
            throw ApiException.invalid("Порог должен быть от 0 до 10");
        }
        var plan = intakeService.occurrences(user, from, to, period.timeZone(), null).items();
        return calculator.overview(period, owner.getDataRevision(),
                ratings.findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(user, from, to), tags.currentForUser(user),
                medications.findByUserIdOrderByCreatedAtAscIdAsc(user), plan,
                intakes.findByUserIdOrderByCreatedAtAscIdAsc(user), threshold);
    }

    public ComparisonResponse comparison(
            UUID user,
            LocalDate from,
            LocalDate to,
            String timeZone,
            TagCode code,
            Metric metric
    ) {
        var owner = access.read(user);
        var period = period(from, to, timeZone == null ? owner.getTimeZone() : timeZone);
        if (code == null || metric == null) {
            throw ApiException.invalid("Укажите тег и метрику");
        }
        var zone = TimeRules.zone(period.timeZone());
        return calculator.comparison(period, owner.getDataRevision(),
                ratings.findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(user, from, to),
                entries.inPeriod(user, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()),
                tags.currentForUser(user), code, metric);
    }

    public CalendarResponse calendar(
            UUID user,
            LocalDate from,
            LocalDate to,
            String timeZone
    ) {
        var owner = access.read(user);
        var period = period(from, to, timeZone == null ? owner.getTimeZone() : timeZone);
        var zone = TimeRules.zone(period.timeZone());
        var state = ratings.findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(user, from, to);
        var notes = entries.inPeriod(user, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
        var confirmed = tags.currentForUser(user);
        var ambiguity = calculator.ambiguities(period, confirmed);
        var plan = intakeService.occurrences(user, from, to, period.timeZone(), null).items();
        var days = from.datesUntil(to.plusDays(1)).map(day -> {
            var slots = new EnumMap<RatingSlot, alex.drankov.diary.ratings.dto.RatingResponse>(RatingSlot.class);
            for (var slot : RatingSlot.values()) {
                slots.put(slot, state.stream().filter(r -> r.getLocalDate().equals(day) && r.getSlot() == slot)
                        .findFirst().map(ratingMapper::response).orElse(null));
            }
            var dayNotes = notes.stream().filter(e -> e.getOccurredAt().atZone(zone).toLocalDate().equals(day)).toList();
            var dayPlan = plan.stream().filter(o -> o.plannedAt().atZone(zone).toLocalDate().equals(day)).toList();
            long reviewed = dayNotes.stream().filter(e -> e.isReviewComplete()).count();
            long taken = dayPlan.stream().filter(o -> o.status() == IntakeStatus.TAKEN).count();
            long skipped = dayPlan.stream().filter(o -> o.status() == IntakeStatus.SKIPPED).count();
            return new CalendarDayResponse(day, Collections.unmodifiableMap(slots), dayNotes.size(), reviewed,
                    dayNotes.size() - reviewed, confirmed.stream().filter(tag -> tag.observed() && day.equals(tag.getAppliesToLocalDate()))
                            .map(tag -> tag.getLabelCode()).distinct().sorted().toList(),
                    ambiguity.stream().filter(value -> value.date().equals(day))
                            .flatMap(value -> value.codes().stream()).toList(),
                    new MedicationDayResponse(dayPlan.size(), taken, skipped, dayPlan.size() - taken - skipped));
        }).toList();
        return new CalendarResponse(period, days, owner.getDataRevision());
    }

    public DayResponse day(
            UUID user,
            LocalDate date,
            String timeZone,
            int page,
            int size
    ) {
        var calendar = calendar(user, date, date, timeZone);
        var zone = TimeRules.zone(calendar.period().timeZone());
        var notes = entries.inPeriod(user, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
        var paging = PageRequest.of(page, size);
        int start = (int) Math.min(paging.getOffset(), notes.size());
        int end = Math.min(start + size, notes.size());
        var result = PageResponse.of(new PageImpl<>(notes.subList(start, end), paging, notes.size()),
                entry -> entryMapper.summary(entry, zone, tagQuery.observedCodes(entry)));
        return new DayResponse(calendar.days().getFirst(), result,
                intakeService.occurrences(user, date, date, calendar.period().timeZone(), null).items(),
                tags.currentForUser(user).stream().filter(tag -> date.equals(tag.getAppliesToLocalDate()))
                        .map(tag -> new ContextTagResponse(tag.getEntryId(), tag.getRevision(), tagMapper.response(tag))).toList(),
                calendar.dataRevision());
    }

    private static PeriodResponse period(
            LocalDate from,
            LocalDate to,
            String zone
    ) {
        TimeRules.range(from, to, 366);
        return new PeriodResponse(from, to, TimeRules.zone(zone).getId());
    }
}
