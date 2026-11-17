package alex.drankov.diary.entries.entity;

import alex.drankov.diary.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "entry_revision", uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "revision"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntryRevision extends BaseEntity {
    @Column(nullable = false, updatable = false)
    private UUID entryId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false, length = 100)
    private String timeZone;

    public EntryRevision(DiaryEntry entry) {
        entryId = entry.getId();
        revision = entry.getRevision();
        text = entry.getText();
        occurredAt = entry.getOccurredAt();
        timeZone = entry.getTimeZone();
    }
}
