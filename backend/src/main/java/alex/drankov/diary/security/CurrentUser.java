package alex.drankov.diary.security;

import alex.drankov.diary.common.api.ApiException;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static UUID id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && auth.isAuthenticated()) {
            return UUID.fromString(token.getToken().getSubject());
        }
        throw new ApiException(401, "UNAUTHENTICATED", "Требуется вход");
    }
}
