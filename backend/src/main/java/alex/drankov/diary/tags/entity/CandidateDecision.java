package alex.drankov.diary.tags.entity;

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
@Table(name = "review_candidate_decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateDecision extends BaseEntity {
    @Column(nullable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private UUID entryId;

    @Column(nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagAction action;

    private LocalDate appliesToLocalDate;

    public CandidateDecision(
            TagReview review,
            UUID candidateId,
            TagAction action,
            LocalDate date
    ) {
        reviewId = review.getId();
        entryId = review.getEntryId();
        revision = review.getRevision();
        this.candidateId = candidateId;
        this.action = action;
        appliesToLocalDate = date;
    }
}
