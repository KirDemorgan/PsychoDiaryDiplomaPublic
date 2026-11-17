package alex.drankov.diary.tags.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "entry_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntryTag extends BaseEntity {
    @Column(nullable = false)
    private UUID reviewId;
    @Column(nullable = false)
    private UUID entryId;
    @Column(nullable = false)
    private int revision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TagCode labelCode;
    @Column(nullable = false, columnDefinition = "text")
    private String quote;
    @Column(name = "start_index", nullable = false)
    private int start;
    @Column(name = "end_index", nullable = false)
    private int end;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagPresence presence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagTemporal temporal;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagSubject subject;
    @Column
    private LocalDate appliesToLocalDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagOrigin origin;

    @Column
    private UUID candidateId;

    public void sourceCandidate(UUID candidateId) {
        this.candidateId = Objects.requireNonNull(candidateId);
    }

    public EntryTag(UUID reviewId, UUID entryId, int revision, TagCode code, String quote, int start, int end,
            TagPresence presence, TagTemporal temporal, TagSubject subject, LocalDate date, TagOrigin origin) {
        this.reviewId = reviewId;
        this.entryId = entryId;
        this.revision = revision;
        labelCode = code;
        this.quote = quote;
        this.start = start;
        this.end = end;
        this.presence = presence;
        this.temporal = temporal;
        this.subject = subject;
        appliesToLocalDate = date;
        this.origin = origin;
    }

    public boolean observed() {
        return presence == TagPresence.PRESENT && subject == TagSubject.SELF
                && temporal != TagTemporal.FUTURE && appliesToLocalDate != null;
    }
}
