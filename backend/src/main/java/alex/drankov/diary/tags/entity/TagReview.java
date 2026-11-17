package alex.drankov.diary.tags.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tag_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TagReview extends BaseEntity {
    @Column(nullable = false)
    private UUID entryId;
    @Column(nullable = false)
    private int revision;
    @Column(nullable = false)
    private boolean complete;

    public TagReview(
            UUID entryId,
            int revision,
            boolean complete
    ) {
        this.entryId = entryId;
        this.revision = revision;
        this.complete = complete;
    }
}
