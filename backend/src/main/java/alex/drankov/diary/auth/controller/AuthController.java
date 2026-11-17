package alex.drankov.diary.auth.controller;

import alex.drankov.diary.auth.dto.AuthResponse;
import alex.drankov.diary.auth.dto.LoginRequest;
import alex.drankov.diary.auth.dto.RefreshTokenRequest;
import alex.drankov.diary.auth.dto.RegisterRequest;
import alex.drankov.diary.auth.security.AuthThrottle;
import alex.drankov.diary.auth.service.AuthService;
import alex.drankov.diary.common.crypto.Hashing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.net.URI;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Вход и сессии")
@SecurityRequirements
public class AuthController {
    private final AuthService auth;
    private final AuthThrottle throttle;

    @PostMapping("/register")
    @Operation(summary = "Регистрация")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest http
    ) {
        throttle.acquire(http.getRemoteAddr(), request.email());
        return ResponseEntity.created(URI.create("/api/v1/me")).body(auth.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        throttle.acquire(http.getRemoteAddr(), request.email());
        return auth.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        throttle.acquire(http.getRemoteAddr(), "refresh:" + Hashing.sha256(request.refreshToken()));
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
