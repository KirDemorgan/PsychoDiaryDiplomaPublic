package alex.drankov.diary.security;

import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.auth.security.SessionJwtValidator;
import alex.drankov.diary.auth.security.Tokens;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.ApiExceptionHandler;
import alex.drankov.diary.common.api.RequestIdFilter;
import alex.drankov.diary.users.controller.ProfileController;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.mapper.UserMapper;
import alex.drankov.diary.users.service.ProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.security;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(ApiSecurityTest.TestConfiguration.class)
@WebAppConfiguration
class ApiSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProfileService profiles;

    @Autowired
    private SessionJwtValidator sessions;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private RequestIdFilter requestIds;

    private MockMvc mvc;
    private User owner;
    private String token;

    @BeforeEach
    void setup() {
        reset(profiles, sessions);
        when(sessions.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
        owner = user();
        token = new Tokens(encoder, security(), Clock.systemUTC()).access(owner,
                new AuthSession(owner.getId(), Instant.now().plusSeconds(3600)));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIds)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Запрос без входа получает безопасную ошибку без создания HttpSession")
    void anonymousRequestHasSafeErrorAndNoHttpSession() throws Exception {
        var result = mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("Некорректный Bearer-токен отклоняется")
    void malformedBearerTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("JWT определяет владельца, ответ профиля содержит ETag")
    void jwtSubjectSelectsOwnerAndResponseContainsVersion() throws Exception {
        when(profiles.get(owner.getId())).thenReturn(Mappers.getMapper(UserMapper.class).response(owner));
        var result = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andReturn();
        verify(profiles).get(owner.getId());
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("Роль без USER не получает доступ к пользовательскому API")
    void wrongRoleCannotAccessUserApi() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + encoded("diary-mobile", "OTHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("JWT с другой аудиторией не проходит аутентификацию")
    void wrongAudienceCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + encoded("another-api", "USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS разрешает настроенный origin и отклоняет неизвестный")
    void corsAllowsConfiguredOriginAndRejectsUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/v1/me")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization,If-Match"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/v1/me")
                        .header("Origin", "https://unlisted.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Необъявленные маршруты и выключенная документация недоступны")
    void routesOutsideApiAndDisabledDocsAreDenied() throws Exception {
        mvc.perform(get("/unlisted").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Доменная ошибка сохраняет код и идентификатор запроса")
    void domainErrorRetainsCodeAndRequestIdentifier() throws Exception {
        when(profiles.get(owner.getId())).thenThrow(ApiException.notFound());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("JWT без аудитории отклоняется с ошибкой аутентификации")
    void missingAudienceIsRejectedAsAuthenticationError() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + encoded(null, "USER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String encoded(String audience, String role) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(security().issuer())
                .subject(owner.getId().toString())
                .audience(audience == null ? List.of() : List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("sid", UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ProfileController.class, ApiExceptionHandler.class, RequestIdFilter.class})
    static class TestConfiguration {

        @Bean
        SecurityProperties securityProperties() {
            return security();
        }

        @Bean
        SessionJwtValidator sessionValidator() {
            return mock(SessionJwtValidator.class);
        }

        @Bean
        ProfileService profiles() {
            return mock(ProfileService.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder()
                    .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                    .build();
        }
    }
}
