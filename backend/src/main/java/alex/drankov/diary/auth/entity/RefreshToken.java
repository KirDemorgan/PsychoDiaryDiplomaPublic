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
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {
    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    public RefreshToken(
            UUID sessionId,
            String hash,
            Instant expiresAt
    ) {
        this.sessionId = sessionId;
        this.tokenHash = hash;
        this.expiresAt = expiresAt;
    }

    public void consume(Instant at) {
        consumedAt = at;
    }
}
