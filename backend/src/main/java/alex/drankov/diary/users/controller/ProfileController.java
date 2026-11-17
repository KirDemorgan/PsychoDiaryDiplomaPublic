package alex.drankov.diary.users.controller;

import alex.drankov.diary.users.dto.UpdateProfileRequest;
import alex.drankov.diary.users.dto.UpdateSettingsRequest;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.security.CurrentUser;
import alex.drankov.diary.users.dto.UserResponse;
import alex.drankov.diary.users.dto.UserSettingsResponse;
import alex.drankov.diary.users.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Профиль и настройки")
public class ProfileController {
    private final ProfileService profiles;

    @GetMapping
    public ResponseEntity<UserResponse> get() {
        return user(profiles.get(CurrentUser.id()));
    }

    @PatchMapping
    @Operation(description = "PATCH: отсутствующее поле сохраняется; displayName=null очищает имя. If-Match обязателен.")
    public ResponseEntity<UserResponse> patch(
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody UpdateProfileRequest body
    ) {
        return user(profiles.patch(CurrentUser.id(), version, body));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> settings() {
        return settings(profiles.settings(CurrentUser.id()));
    }

    @PatchMapping("/settings")
    @Operation(description = "Включение AI требует aiConsentVersion=diary-ai-v1. Время согласия задаёт сервер. If-Match обязателен.")
    public ResponseEntity<UserSettingsResponse> patchSettings(
            @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody UpdateSettingsRequest body
    ) {
        return settings(profiles.patchSettings(CurrentUser.id(), version, body));
    }

    private ResponseEntity<UserResponse> user(UserResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }

    private ResponseEntity<UserSettingsResponse> settings(UserSettingsResponse value) {
        return ResponseEntity.ok().eTag(Versions.etag(value.version())).body(value);
    }
}
