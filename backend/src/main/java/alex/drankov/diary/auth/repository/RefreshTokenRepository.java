package alex.drankov.diary.auth.repository;

import alex.drankov.diary.auth.entity.RefreshToken;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Query(value = "select s.user_id from refresh_token r join auth_session s on s.id=r.session_id where r.token_hash=:hash", nativeQuery = true)
    Optional<UUID> ownerByHash(String hash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
