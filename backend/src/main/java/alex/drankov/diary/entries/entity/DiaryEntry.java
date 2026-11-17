package alex.drankov.diary.entries.entity;

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
@Table(name = "diary_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryEntry extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int revision = 1;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 100)
    private String timeZone;

    @Column(nullable = false)
    private boolean reportVisible;

    @Column(nullable = false)
    private boolean reviewComplete;

    @Column
    private UUID currentReviewId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    public DiaryEntry(
            UUID user,
            String text,
            Instant at,
            String zone,
            boolean visible
    ) {
        userId = user;
        this.text = text;
        occurredAt = at;
        timeZone = zone;
        reportVisible = visible;
    }

    public void revise(String text, Instant at, String zone) {
        this.text = text;
        occurredAt = at;
        timeZone = zone;
        revision++;
        reviewComplete = false;
        currentReviewId = null;
    }

    public void review(UUID reviewId, boolean complete) {
        currentReviewId = reviewId;
        reviewComplete = complete;
    }

    public void visibility(boolean value) {
        reportVisible = value;
    }
}
