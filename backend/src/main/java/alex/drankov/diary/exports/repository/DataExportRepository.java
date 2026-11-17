package alex.drankov.diary.exports.repository;

import alex.drankov.diary.exports.entity.DataExport;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DataExportRepository extends JpaRepository<DataExport, UUID> {
    Optional<DataExport> findByIdAndUserId(UUID id, UUID user);

    @Modifying
    @Query("update DataExport e set e.archive = null, e.errorCode = 'EXPORT_EXPIRED' where e.expiresAt <= :now and e.archive is not null")
    int eraseExpired(Instant now);
}
