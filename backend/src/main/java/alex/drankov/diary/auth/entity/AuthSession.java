package alex.drankov.diary.auth.entity;

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
@Table(name = "auth_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;

    public AuthSession(
            UUID userId,
            Instant expiresAt
    ) {
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public boolean active(Instant at) {
        return revokedAt == null && expiresAt.isAfter(at);
    }
}
