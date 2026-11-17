package alex.drankov.diary.auth.repository;

import alex.drankov.diary.auth.entity.AuthSession;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
}
