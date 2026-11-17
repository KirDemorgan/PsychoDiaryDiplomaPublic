package alex.drankov.diary.ai.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTask extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    private UUID entryId;
    private UUID reportId;
    private Integer revision;

    @Column(nullable = false)
    private long sourceDataRevision;

    @Column(nullable = false, length = 64)
    private String inputHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiStatus status = AiStatus.QUEUED;

    @Column(nullable = false)
    private int attempt;

    private Instant finishedAt;
    private Instant leaseUntil;
    private UUID leaseToken;
    private String modelId;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String schemaVersion;

    @Column(length = 100)
    private String errorCode;

    public AiTask(UUID user, UUID entry, UUID report, Integer revision, long dataRevision, String hash,
            String modelId, AiKind kind) {
        userId = user;
        entryId = entry;
        reportId = report;
        this.revision = revision;
        sourceDataRevision = dataRevision;
        inputHash = hash;
        this.modelId = modelId;
        this.kind = kind;
        promptVersion = kind == AiKind.ENTRY ? "entry-extraction-v2" : "report-overview-v6";
        schemaVersion = kind == AiKind.ENTRY ? "entry-candidates-v1" : "report-narrative-v1";
    }

    public boolean runnable(Instant now) {
        return status == AiStatus.QUEUED || status == AiStatus.RUNNING && leaseUntil != null && !leaseUntil.isAfter(now);
    }

    public void claim(
            UUID token,
            Instant until
    ) {
        status = AiStatus.RUNNING;
        leaseToken = token;
        leaseUntil = until;
        attempt++;
        errorCode = null;
    }

    public void retry(String code) {
        status = AiStatus.QUEUED;
        leaseToken = null;
        leaseUntil = null;
        errorCode = code;
    }

    public void finish(
            AiStatus state,
            String code,
            Instant at
    ) {
        status = state;
        errorCode = code;
        finishedAt = at;
        leaseToken = null;
        leaseUntil = null;
    }
}
