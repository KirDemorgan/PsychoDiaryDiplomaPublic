package alex.drankov.diary.ratings.entity;

import alex.drankov.diary.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "state_rating", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "local_date", "slot"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StateRating extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RatingSlot slot;

    @Column(nullable = false, length = 100)
    private String timeZone;
    private Integer anxiety;
    private Integer mood;
    private Integer energy;

    public StateRating(
            UUID user,
            LocalDate date,
            RatingSlot slot
    ) {
        userId = user;
        localDate = date;
        this.slot = slot;
    }

    public void replace(
            Integer anxiety,
            Integer mood,
            Integer energy,
            String timeZone
    ) {
        this.anxiety = anxiety;
        this.mood = mood;
        this.energy = energy;
        this.timeZone = timeZone;
    }
}
