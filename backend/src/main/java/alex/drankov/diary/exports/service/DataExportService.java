package alex.drankov.diary.exports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.exports.dto.ExportJobResponse;
import alex.drankov.diary.exports.entity.DataExport;
import alex.drankov.diary.exports.repository.DataExportRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataExportService {
    private final DataExportRepository exports;
    private final ExportCollector collector;
    private final ExportArchive archives;
    private final UserAccess access;
    private final Idempotency idempotency;
    private final Clock clock;

    @Transactional
    public MutationResult<ExportJobResponse> create(
            UUID user,
            UUID key
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /data-exports", key, "diary-export-v1", ExportJobResponse.class, () -> {
            var now = clock.instant();
            byte[] archive = archives.create(collector.collect(user, owner.getDataRevision(), now));
            var value = exports.saveAndFlush(new DataExport(user, owner.getDataRevision(), now.plus(Duration.ofHours(24)), archive));
            return new MutationResult<>(response(value), value.getId(), value.getVersion(), 202);
        });
    }

    public ExportJobResponse get(
            UUID user,
            UUID id
    ) {
        return response(find(user, id));
    }

    public byte[] file(
            UUID user,
            UUID id
    ) {
        var value = find(user, id);
        if (value.getArchive() == null || !clock.instant().isBefore(value.getExpiresAt())) {
            throw new ApiException(410, "EXPORT_GONE", "Экспорт очищен или срок хранения истёк");
        }
        return value.getArchive().clone();
    }

    @Transactional
    public void delete(
            UUID user,
            UUID id
    ) {
        access.lock(user);
        var value = find(user, id);
        idempotency.erase(user, id);
        exports.delete(value);
    }

    @Scheduled(fixedDelayString = "${app.exports.cleanup-delay:PT15M}")
    @Transactional
    public void cleanup() {
        exports.eraseExpired(clock.instant());
    }

    private DataExport find(
            UUID user,
            UUID id
    ) {
        return exports.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
    }

    private ExportJobResponse response(DataExport value) {
        boolean available = value.getArchive() != null && clock.instant().isBefore(value.getExpiresAt());
        return new ExportJobResponse(value.getId(), available ? "READY" : "EXPIRED", value.getCreatedAt(),
                value.getExpiresAt(), value.getSourceDataRevision(), value.getErrorCode());
    }
}
