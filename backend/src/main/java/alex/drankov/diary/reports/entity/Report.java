package alex.drankov.diary.reports.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false, length = 100)
    private String timeZone;

    @Column(nullable = false)
    private long sourceDataRevision;

    @Column(nullable = false, length = 64)
    private String inputHash;

    @Column(columnDefinition = "text")
    private String snapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(length = 100)
    private String errorCode;

    public Report(
            UUID userId,
            LocalDate from,
            LocalDate to,
            String zone,
            long revision,
            String hash,
            String snapshot
    ) {
        this.userId = userId;
        fromDate = from;
        toDate = to;
        timeZone = zone;
        sourceDataRevision = revision;
        inputHash = hash;
        snapshotJson = snapshot;
        status = ReportStatus.READY;
    }

    public void queue() {
        status = ReportStatus.QUEUED;
    }

    public void running() {
        status = ReportStatus.RUNNING;
    }

    public void ready(String snapshot, String error) {
        snapshotJson = snapshot;
        status = ReportStatus.READY;
        errorCode = error;
    }

    public void cancel() {
        status = ReportStatus.CANCELLED;
    }
}
