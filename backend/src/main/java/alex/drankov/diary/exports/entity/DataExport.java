package alex.drankov.diary.exports.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "data_export")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataExport extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private long sourceDataRevision;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(columnDefinition = "bytea")
    private byte[] archive;

    @Column(length = 100)
    private String errorCode;

    public DataExport(
            UUID userId,
            long revision,
            Instant expiresAt,
            byte[] archive
    ) {
        this.userId = userId;
        sourceDataRevision = revision;
        this.expiresAt = expiresAt;
        this.archive = archive.clone();
    }
}
