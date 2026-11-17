package alex.drankov.diary.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void createTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
