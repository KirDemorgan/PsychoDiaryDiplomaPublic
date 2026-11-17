package alex.drankov.diary.entries.service;

import alex.drankov.diary.ai.service.AnalysisQuery;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.LikePattern;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Texts;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.dto.CreateEntryRequest;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.dto.EntrySummaryResponse;
import alex.drankov.diary.entries.dto.UpdateEntryRequest;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.entity.EntryRevision;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.entries.repository.EntryRevisionRepository;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.service.TagQuery;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryService {
    private static final int MAX_QUERY_LENGTH = 200;
    private static final Instant EARLIEST = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    private final EntryRepository entries;
    private final EntryRevisionRepository revisions;
    private final EntryMapper mapper;
    private final UserAccess access;
    private final Idempotency idempotency;
    private final TagQuery tags;
    private final AnalysisQuery analyses;

    public EntryResponse get(
            UUID user,
            UUID id
    ) {
        return response(find(user, id));
    }

    @Transactional
    public MutationResult<EntryResponse> create(
            UUID user,
            UUID key,
            CreateEntryRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /entries", key, request, EntryResponse.class, () -> {
            validateText(request.text());
            TimeRules.occurrence(request.occurredAt(), request.timeZone());
            var entry = entries.saveAndFlush(new DiaryEntry(user, request.text(), request.occurredAt().toInstant(),
                    request.timeZone(), request.reportVisible() == null || request.reportVisible()));
            revisions.saveAndFlush(new EntryRevision(entry));
            owner.advanceDataRevision();
            return new MutationResult<>(response(entry), entry.getId(), entry.getVersion(), 201);
        });
    }

    @Transactional
    public EntryResponse patch(
            UUID user,
            UUID id,
            String ifMatch,
            UpdateEntryRequest request
    ) {
        var owner = access.lock(user);
        var entry = find(user, id);
        Versions.require(ifMatch, entry.getVersion());
        request.validate();
        String text = request.hasText() ? request.getText() : entry.getText();
        String zone = request.hasTimeZone() ? request.getTimeZone() : entry.getTimeZone();
        TimeRules.zone(zone);
        validateText(text);
        Instant at = entry.getOccurredAt();
        if (request.hasOccurredAt()) {
            OffsetDateTime date = request.getOccurredAt();
            TimeRules.occurrence(date, zone);
            at = date.toInstant();
        }
        if (!text.equals(entry.getText()) || !at.equals(entry.getOccurredAt()) || !zone.equals(entry.getTimeZone())) {
            entry.revise(text, at, zone);
            revisions.save(new EntryRevision(entry));
        }
        if (request.hasReportVisible()) {
            entry.visibility(request.getReportVisible());
        }
        owner.advanceDataRevision();
        entries.flush();
        return response(entry);
    }

    @Transactional
    public void delete(
            UUID user,
            UUID id,
            String ifMatch
    ) {
        var owner = access.lock(user);
        var entry = find(user, id);
        Versions.require(ifMatch, entry.getVersion());
        idempotency.erase(user, id);
        entries.delete(entry);
        owner.advanceDataRevision();
    }

    public PageResponse<EntrySummaryResponse> list(
            UUID user,
            LocalDate from,
            LocalDate to,
            String timeZone,
            String query,
            int page,
            int size
    ) {
        var zone = resolveZone(user, timeZone);
        var paging = paging(page, size);
        validateQuery(query);
        String q = query == null ? "" : query;
        Page<DiaryEntry> result;
        if (from != null || to != null) {
            TimeRules.range(from, to, 366);
            result = entries.list(user, startOf(from, zone), endOf(to, zone), LikePattern.contains(q), paging);
        } else {
            result = entries.findByUserIdAndTextContainingIgnoreCase(user, q, paging);
        }
        return PageResponse.of(result, e -> summary(e, zone));
    }

    public PageResponse<EntrySummaryResponse> list(UUID user, LocalDate from, LocalDate to, String timeZone,
            String query, TagCode confirmedTag, int page, int size) {
        if (confirmedTag == null) {
            return list(user, from, to, timeZone, query, page, size);
        }
        var zone = resolveZone(user, timeZone);
        if (from != null || to != null) {
            TimeRules.range(from, to, 366);
        }
        validateQuery(query);
        var result = entries.listTagged(user, from == null ? EARLIEST : startOf(from, zone),
                to == null ? LATEST : endOf(to, zone), LikePattern.contains(query == null ? "" : query),
                confirmedTag, paging(page, size));
        return PageResponse.of(result, entry -> summary(entry, zone));
    }

    private ZoneId resolveZone(
            UUID user,
            String timeZone
    ) {
        return TimeRules.zone(timeZone == null ? access.read(user).getTimeZone() : timeZone);
    }

    private static PageRequest paging(
            int page,
            int size
    ) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    }

    private static void validateQuery(String query) {
        if (query != null && Texts.exceeds(query, MAX_QUERY_LENGTH)) {
            throw ApiException.invalid("Поисковый запрос слишком длинный");
        }
    }

    private static Instant startOf(
            LocalDate date,
            ZoneId zone
    ) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static Instant endOf(
            LocalDate date,
            ZoneId zone
    ) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private EntrySummaryResponse summary(
            DiaryEntry entry,
            ZoneId zone
    ) {
        var analysis = analyses.latest(entry);
        return mapper.summary(entry, zone, tags.observedCodes(entry), analysis == null ? null : analysis.status().name());
    }

    private EntryResponse response(DiaryEntry entry) {
        return mapper.response(entry, tags.current(entry), analyses.latest(entry));
    }

    private DiaryEntry find(
            UUID user,
            UUID id
    ) {
        return entries.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
    }

    public static void validateText(String text) {
        if (text == null || text.isBlank() || Texts.exceeds(text, 5000)) {
            throw ApiException.invalid("Текст должен содержать от 1 до 5000 символов");
        }
    }
}
