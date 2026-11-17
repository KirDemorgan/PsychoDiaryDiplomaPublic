package alex.drankov.diary.users.repository;

import alex.drankov.diary.users.entity.UserSettings;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByUserId(UUID userId);
}
