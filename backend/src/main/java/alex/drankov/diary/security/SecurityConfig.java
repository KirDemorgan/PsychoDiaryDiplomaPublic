package alex.drankov.diary.security;

import alex.drankov.diary.auth.security.SessionJwtValidator;
import alex.drankov.diary.common.api.Problems;

import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecretKey signingKey(SecurityProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.secretBase64());
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret requires at least 32 random bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder encoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder decoder(
            SecretKey key,
            SecurityProperties properties,
            SessionJwtValidator sessions
    ) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience, sessions));
        return decoder;
    }

    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain api(
            HttpSecurity http,
            JsonMapper json,
            @Value("${springdoc.api-docs.enabled:false}")
            boolean docs
    ) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        http.csrf(AbstractHttpConfigurer::disable) // Bearer headers only; no authentication cookies.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    if (docs) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/api/v1/**").hasRole("USER").anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setHeader("WWW-Authenticate", "Bearer");
                            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(res.getOutputStream(),
                                    Problems.create(401, "UNAUTHENTICATED", "Требуется вход", req));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(res.getOutputStream(),
                                    Problems.create(403, "FORBIDDEN", "Действие запрещено", req));
                        }))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setHeader("WWW-Authenticate", "Bearer");
                            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(res.getOutputStream(),
                                    Problems.create(401, "UNAUTHENTICATED", "Требуется вход", req));
                        }));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("ETag", "Location", "X-Request-Id"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
