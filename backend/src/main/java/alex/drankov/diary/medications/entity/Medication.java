package alex.drankov.diary.medications.entity;

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
@Table(name = "medication")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String form;

    @Column
    private Instant archivedAt;

    public Medication(
            UUID userId,
            String name,
            String form
    ) {
        this.userId = userId;
        rename(name, form);
    }

    public void rename(
            String name,
            String form
    ) {
        this.name = name;
        this.form = form;
    }

    public void archive(Instant at) {
        archivedAt = at;
    }

    public void touch() {
        updateTimestamp();
    }
}
