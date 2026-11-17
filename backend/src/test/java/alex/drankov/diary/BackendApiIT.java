package alex.drankov.diary;

import alex.drankov.diary.ai.adapter.ModelAdapter;
import alex.drankov.diary.ai.adapter.ModelGateway;
import alex.drankov.diary.ai.adapter.ModelRequest;
import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.ai.service.AiQueue;
import alex.drankov.diary.ai.service.AiResultValidator;
import alex.drankov.diary.ai.service.AiWorker;

import alex.drankov.diary.speech.WavFixtures;
import alex.drankov.diary.speech.adapter.SpeechAdapter;
import alex.drankov.diary.speech.adapter.SpeechGateway;
import alex.drankov.diary.speech.service.SpeechQueue;
import alex.drankov.diary.speech.service.SpeechWorker;
import alex.drankov.diary.speech.service.SpeechCleanup;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only Failsafe's integration-tests profile runs this class; unit tests require no Docker.
 */
@Testcontainers
@Import(BackendApiIT.ModelConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.auth-max-attempts=1000",
        "spring.profiles.active=local", "app.ai.worker-enabled=false", "app.speech.worker-enabled=false"})
class BackendApiIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    HttpClient http = HttpClient.newHttpClient();

    @Autowired
    AiQueue aiQueue;
    @Autowired
    ModelGateway modelGateway;
    @Autowired
    AiResultValidator aiValidator;
    @Autowired
    ControlledModel controlledModel;

    @Autowired
    SpeechQueue speechQueue;
    @Autowired
    SpeechGateway speechGateway;
    @Autowired
    SpeechCleanup speechCleanup;
    @Autowired
    ControlledSpeech controlledSpeech;

    @TestConfiguration(proxyBeanMethods = false)
    static class ModelConfiguration {
        @Bean
        @Primary
        ControlledModel controlledModel() {
            return new ControlledModel();
        }

        @Bean
        @Primary
        ControlledSpeech controlledSpeech() {
            return new ControlledSpeech();
        }
    }

    static class ControlledSpeech implements SpeechAdapter {
        @Override
        public String modelId() {
            return "controlled-speech-adapter";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String transcribe(byte[] wav) {
            return "Сегодня тревожно после работы";
        }
    }

    void enableSpeech(Account owner) throws Exception {
        enableAi(owner);
        var consent = call("GET", "/speech/consent", owner.access(), null, Map.of());
        assertThat(call("PUT", "/speech/consent", owner.access(),
                Map.of("enabled", true, "consentVersion", "diary-speech-v1"),
                Map.of("If-Match", etag(consent))).status()).isEqualTo(200);
    }

    Reply upload(Account owner, UUID key, byte[] audio) throws Exception {
        String boundary = "diary-test-boundary";
        var bytes = new ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"record.wav\""
                + "\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(audio);
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/speech/transcriptions"))
                .header("Authorization", "Bearer " + owner.access()).header("Idempotency-Key", key.toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), json.readTree(response.body()), response.headers());
    }

    @Test
    @DisplayName("Голос требует отдельное согласие; upload идемпотентен, результат изолирован и не создаёт запись автоматически")
    void speechTranscription() throws Exception {
        var owner = register();
        var other = register();
        enableAi(owner);
        assertThat(upload(owner, UUID.randomUUID(), WavFixtures.wav(16000)).status()).isEqualTo(403);
        enableSpeech(owner);
        UUID key = UUID.randomUUID();
        var created = upload(owner, key, WavFixtures.wav(16000));
        assertThat(created.status()).isEqualTo(202);
        String id = created.body().get("id").asString();
        assertThat(upload(owner, key, WavFixtures.wav(16000)).body().get("id").asString()).isEqualTo(id);
        assertThat(upload(owner, key, WavFixtures.wav(16001)).status()).isEqualTo(409);
        assertThat(call("GET", "/speech/transcriptions/" + id, other.access(), null, Map.of()).status()).isEqualTo(404);
        assertThat(call("POST", "/speech/transcriptions/" + id + "/cancel", other.access(), null,
                Map.of()).status()).isEqualTo(404);
        new SpeechWorker(speechQueue, controlledSpeech, speechGateway).tick();
        var completed = call("GET", "/speech/transcriptions/" + id, owner.access(), null, Map.of());
        assertThat(completed.body().get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(completed.body().get("transcript").asString()).isEqualTo("Сегодня тревожно после работы");
        UUID user = jdbc.queryForObject("select user_id from transcription where id=?", UUID.class,
                UUID.fromString(id));
        assertThat(jdbc.queryForObject("select audio is null from transcription where id=?", Boolean.class,
                UUID.fromString(id))).isTrue();
        assertThat(
                jdbc.queryForObject("select count(*) from diary_entry where user_id=?", Integer.class, user)).isZero();
        assertThat(upload(owner, UUID.randomUUID(), new byte[]{1, 2, 3}).status()).isEqualTo(415);
        assertThat(upload(owner, UUID.randomUUID(), new byte[4 * 1024 * 1024 + 1]).status()).isEqualTo(413);
    }

    @Test
    @DisplayName("Отмена отбрасывает поздний ASR ответ; удаление и TTL стирают аудио и защищают повтор upload")
    void speechCancelDeleteExpiry() throws Exception {
        var owner = register();
        enableSpeech(owner);
        UUID key = UUID.randomUUID();
        var created = upload(owner, key, WavFixtures.wav(16000));
        String id = created.body().get("id").asString();
        var work = speechQueue.claim();
        assertThat(work.id()).isEqualTo(UUID.fromString(id));
        assertThat(call("POST", "/speech/transcriptions/" + id + "/cancel", owner.access(), null,
                Map.of()).status()).isEqualTo(200);
        speechQueue.succeed(work, "Поздний текст");
        assertThat(jdbc.queryForObject("select transcript is null and audio is null from transcription where id=?",
                Boolean.class, UUID.fromString(id))).isTrue();
        assertThat(call("DELETE", "/speech/transcriptions/" + id, owner.access(), null, Map.of()).status()).isEqualTo(
                204);
        assertThat(upload(owner, key, WavFixtures.wav(16000)).status()).isEqualTo(410);
        key = UUID.randomUUID();
        id = upload(owner, key, WavFixtures.wav(16000)).body().get("id").asString();
        jdbc.update("update transcription set expires_at=CURRENT_TIMESTAMP-interval '1 minute' where id=?",
                UUID.fromString(id));
        assertThat(call("GET", "/speech/transcriptions/" + id, owner.access(), null, Map.of()).status()).isEqualTo(410);
        speechCleanup.tick();
        assertThat(jdbc.queryForObject("select audio is null and status='EXPIRED' from transcription where id=?",
                Boolean.class, UUID.fromString(id))).isTrue();
        assertThat(upload(owner, key, WavFixtures.wav(16000)).status()).isEqualTo(410);
    }

    @Test
    @DisplayName("Отзыв голосового согласия стирает готовый текст и ZIP с ним; удаление аккаунта каскадно удаляет ASR задачи")
    void speechRevocationAndAccountDeletion() throws Exception {
        var owner = register();
        enableSpeech(owner);
        String id = upload(owner, UUID.randomUUID(), WavFixtures.wav(16000)).body().get("id").asString();
        new SpeechWorker(speechQueue, controlledSpeech, speechGateway).tick();
        var exported = call("POST", "/data-exports", owner.access(), null, key(UUID.randomUUID().toString()));
        assertThat(exported.status()).isEqualTo(202);
        var consent = call("GET", "/speech/consent", owner.access(), null, Map.of());
        assertThat(call("PUT", "/speech/consent", owner.access(), Map.of("enabled", false),
                Map.of("If-Match", etag(consent))).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "select transcript is null and audio is null and status='CANCELLED' from transcription where id=?",
                Boolean.class, UUID.fromString(id))).isTrue();
        assertThat(call("GET", "/data-exports/" + exported.body().get("id").asString() + "/file",
                owner.access(), null, Map.of()).status()).isEqualTo(410);
        assertThat(
                call("DELETE", "/me", owner.access(), Map.of("confirm", true, "currentPassword", "demo-password-1234"),
                        Map.of()).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from transcription where id=?", Integer.class,
                UUID.fromString(id))).isZero();
    }

    static class ControlledModel implements ModelAdapter {
        volatile boolean invalid;

        @Override
        public String modelId() {
            return "controlled-test-adapter";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String generate(ModelRequest request) {
            if (invalid) {
                return "invalid JSON";
            }
            if (request.kind() == AiKind.ENTRY) {
                return """
                        {"candidates":[{"labelCode":"ANXIETY","quote":"тревожно","occurrence":0,
                        "presence":"PRESENT","temporal":"CURRENT","subject":"SELF"}]}
                        """;
            }
            var mapper = JsonMapper.builder().build();
            var fact = mapper.readTree(request.inputJson()).get(0);
            return mapper.writeValueAsString(Map.of("claims", List.of(Map.of(
                    "text", fact.get("allowedText"), "factIds", List.of(fact.get("id"))))));
        }
    }

    void aiTick() {
        new AiWorker(aiQueue, controlledModel, modelGateway, aiValidator).tick();
    }

    void enableAi(Account owner) throws Exception {
        var settings = call("GET", "/me/settings", owner.access(), null, Map.of());
        assertThat(call("PATCH", "/me/settings", owner.access(),
                Map.of("aiEnabled", true, "aiConsentVersion", "diary-ai-v1"),
                Map.of("If-Match", etag(settings))).status()).isEqualTo(200);
    }

    record Account(String access, String refresh) {
    }

    record Reply(int status, JsonNode body, HttpHeaders headers) {
    }

    Account register() throws Exception {
        var response = call("POST", "/auth/register", null, Map.of("email", UUID.randomUUID() + "@example.test",
                "password", "demo-password-1234", "timeZone", "Asia/Yekaterinburg", "displayName", "Demo"), Map.of());
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("user").has("passwordHash")).isFalse();
        return new Account(response.body().get("accessToken").asString(),
                response.body().get("refreshToken").asString());
    }

    Reply call(String method,
            String path,
            String token,
            Object body,
            Map<String, String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        headers.forEach(builder::header);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(
                json.writeValueAsString(body));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var response = http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()),
                response.headers());
    }

    Map<String, String> key(String value) {
        return Map.of("Idempotency-Key", value);
    }

    String etag(Reply response) {
        return response.headers().firstValue("ETag").orElseThrow();
    }

    private Map<String, Object> rating(
            Integer anxiety,
            Integer mood,
            Integer energy
    ) {
        var body = new HashMap<String, Object>();
        body.put("anxiety", anxiety);
        body.put("mood", mood);
        body.put("energy", energy);
        body.put("timeZone", "UTC");
        return body;
    }

    Map<String, Object> entry(String text) {
        return Map.of("text", text, "occurredAt", "2026-10-13T15:00:00+05:00", "timeZone", "Asia/Yekaterinburg");
    }

    @Test
    @DisplayName("Миграции и OpenAPI доступны, защищённый API требует входа")
    void migrationsAndOpenApiExistAndUnauthenticatedAccessFails() throws Exception {
        assertThat(jdbc.queryForList("select id from databasechangelog", String.class))
                .contains("001-users", "002-diary", "003-medications");
        var missing = call("GET", "/me", null, null, Map.of());
        assertThat(missing.status()).isEqualTo(401);
        assertThat(missing.body().get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(missing.headers().firstValue("X-Request-Id")).isPresent();
        var docs = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("bearerAuth", "/api/v1/entries");
    }

    @Test
    @DisplayName("API записей проверяет владельца, версии, повторы и удаление чувствительных данных")
    void entriesHaveOwnershipVersionsReplayAndDeletionPrivacy() throws Exception {
        var first = register();
        var second = register();
        String operation = UUID.randomUUID().toString();
        var created = call("POST", "/entries", first.access(), entry("😀".repeat(5000)), key(operation));
        assertThat(created.status()).isEqualTo(201);
        String path = "/entries/" + created.body().get("id").asString();
        assertThat(created.body().get("revision").asInt()).isEqualTo(1);
        var replay = call("POST", "/entries", first.access(), entry("😀".repeat(5000)), key(operation));
        assertThat(replay.body()).isEqualTo(created.body());
        assertThat(call("POST", "/entries", first.access(), entry("different"), key(operation)).status()).isEqualTo(
                409);
        assertThat(call("GET", path, second.access(), null, Map.of()).status()).isEqualTo(404);
        assertThat(call("PATCH", path, second.access(), Map.of("text", "stolen"),
                Map.of("If-Match", etag(created))).status()).isEqualTo(404);
        assertThat(call("DELETE", path, second.access(), null, Map.of("If-Match", etag(created))).status()).isEqualTo(
                404);
        assertThat(call("PATCH", path, first.access(), Map.of("text", "new"), Map.of()).status()).isEqualTo(428);
        var edited = call("PATCH", path, first.access(), Map.of("text", "new"), Map.of("If-Match", etag(created)));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body().get("revision").asInt()).isEqualTo(2);
        assertThat(call("PATCH", path, first.access(), Map.of("text", "lost"),
                Map.of("If-Match", etag(created))).status()).isEqualTo(412);
        assertThat(jdbc.queryForObject("select count(*) from entry_revision where entry_id=?", Integer.class,
                UUID.fromString(created.body().get("id").asString()))).isEqualTo(2);
        assertThat(call("DELETE", path, first.access(), null, Map.of("If-Match", etag(edited))).status()).isEqualTo(
                204);
        assertThat(
                call("POST", "/entries", first.access(), entry("😀".repeat(5000)), key(operation)).status()).isEqualTo(
                410);
        assertThat(jdbc.queryForObject("select count(*) from entry_revision where entry_id=?", Integer.class,
                UUID.fromString(created.body().get("id").asString()))).isZero();
        assertThat(
                jdbc.queryForObject("select response_payload from mutation_receipt where operation_id=?", String.class,
                        UUID.fromString(operation))).isNull();
    }

    @Test
    @DisplayName("API оценок различает null и ноль, проверяет поля и версию при обновлении")
    void ratingsHaveNullZeroRequiredFieldsAndConditionalUpdate() throws Exception {
        var owner = register();
        var other = register();
        String path = "/ratings/2026-10-13/MORNING", operation = UUID.randomUUID().toString();
        var payload = json.readTree("{\"anxiety\":0,\"mood\":null,\"energy\":10,\"timeZone\":\"UTC\"}");
        var created = call("PUT", path, owner.access(), payload, key(operation));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("mood").isNull()).isTrue();
        assertThat(created.body().get("anxiety").asInt()).isZero();
        assertThat(call("PUT", path, owner.access(), payload, key(operation)).body()).isEqualTo(created.body());
        assertThat(call("GET", path, other.access(), null, Map.of()).status()).isEqualTo(404);
        assertThat(call("PUT", path, owner.access(), payload, key(UUID.randomUUID().toString())).status()).isEqualTo(
                428);
        var changed = json.readTree("{\"anxiety\":2,\"mood\":null,\"energy\":null,\"timeZone\":\"UTC\"}");
        var edited = call("PUT", path, owner.access(), changed,
                Map.of("Idempotency-Key", UUID.randomUUID().toString(), "If-Match", etag(created)));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(call("PUT", "/ratings/2026-10-14/DAY", owner.access(), Map.of("anxiety", 0, "timeZone", "UTC"),
                key(UUID.randomUUID().toString())).status()).isEqualTo(400);
        assertThat(call("DELETE", path, owner.access(), null, Map.of("If-Match", etag(edited))).status()).isEqualTo(
                204);
        assertThat(call("PUT", path, owner.access(), payload, key(operation)).status()).isEqualTo(410);
    }

    @Test
    @DisplayName("API профиля и настроек проверяет согласие, пагинацию, данные и ошибки доступа")
    void profileSettingsPaginationValidationAndSecurityErrors() throws Exception {
        var account = register();
        var me = call("GET", "/me", account.access(), null, Map.of());
        var cleared = call("PATCH", "/me", account.access(), json.readTree("{\"displayName\":null}"),
                Map.of("If-Match", etag(me)));
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body().get("displayName").isNull()).isTrue();
        var settings = call("GET", "/me/settings", account.access(), null, Map.of());
        assertThat(call("PATCH", "/me/settings", account.access(), Map.of("aiEnabled", true),
                Map.of("If-Match", etag(settings))).status()).isEqualTo(403);
        var consent = call("PATCH", "/me/settings", account.access(),
                Map.of("aiEnabled", true, "aiConsentVersion", "diary-ai-v1"), Map.of("If-Match", etag(settings)));
        assertThat(consent.status()).isEqualTo(200);
        assertThat(consent.body().get("aiConsentAcceptedAt").isNull()).isFalse();
        assertThat(call("POST", "/entries", account.access(), entry("😀".repeat(5001)),
                key(UUID.randomUUID().toString())).status()).isEqualTo(400);
        assertThat(call("POST", "/entries", account.access(), entry("one"),
                key(UUID.randomUUID().toString())).status()).isEqualTo(201);
        var list = call("GET", "/entries?from=2026-10-13&to=2026-10-13&timeZone=Asia/Yekaterinburg", account.access(),
                null, Map.of());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get("items").size()).isEqualTo(1);
        assertThat(call("GET", "/entries?size=101", account.access(), null, Map.of()).status()).isEqualTo(400);
        assertThat(call("GET", "/entries/not-a-uuid", account.access(), null, Map.of()).status()).isEqualTo(400);
        assertThat(call("GET", "/entries/" + UUID.randomUUID(), account.access(), null, Map.of()).status()).isEqualTo(
                404);
        assertThat(call("POST", "/entries", account.access(), entry("one"), Map.of()).status()).isEqualTo(400);
        assertThat(call("GET", "/me", "invalid", null, Map.of()).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Повтор refresh и выход немедленно прекращают доступ выданного JWT")
    void refreshRotationReuseAndLogoutInvalidateAccessImmediately() throws Exception {
        var owner = register();
        var rotated = call("POST", "/auth/refresh", null, Map.of("refreshToken", owner.refresh()), Map.of());
        assertThat(rotated.status()).isEqualTo(200);
        String access = rotated.body().get("accessToken").asString();
        assertThat(call("GET", "/me", access, null, Map.of()).status()).isEqualTo(200);
        assertThat(call("POST", "/auth/refresh", null, Map.of("refreshToken", owner.refresh()),
                Map.of()).status()).isEqualTo(401);
        assertThat(call("GET", "/me", access, null, Map.of()).status()).isEqualTo(401);
        var another = register();
        assertThat(call("POST", "/auth/logout", null, Map.of("refreshToken", another.refresh()),
                Map.of()).status()).isEqualTo(204);
        assertThat(call("GET", "/me", another.access(), null, Map.of()).status()).isEqualTo(401);
        assertThat(call("POST", "/auth/logout", null, Map.of("refreshToken", another.refresh()),
                Map.of()).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("PostgreSQL обеспечивает ограничения значений и внешние ключи")
    void postgresChecksAndForeignKeysAreReal() throws Exception {
        var account = register();
        var created = call("POST", "/entries", account.access(), entry("text"), key(UUID.randomUUID().toString()));
        var id = UUID.fromString(created.body().get("id").asString());
        assertThatThrownBy(() -> jdbc.update("update diary_entry set revision=999 where id=?", id)).isInstanceOf(
                org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update diary_entry set text='' where id=?", id)).isInstanceOf(
                org.springframework.dao.DataIntegrityViolationException.class);
        var me = call("GET", "/me", account.access(), null, Map.of());
        jdbc.update("delete from app_user where id=?", UUID.fromString(me.body().get("id").asString()));
        assertThat(call("GET", "/me", account.access(), null, Map.of()).status()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from diary_entry where id=?", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("Препараты сохраняют версии схем, уникальность отметок и изоляцию владельца")
    void medicationSchedulesAndIntakes() throws Exception {
        var owner = register();
        var other = register();
        var created = call("POST", "/medications", owner.access(), Map.of("name", "Синтетический препарат"),
                key(UUID.randomUUID().toString()));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().get("id").asString();
        assertThat(call("GET", "/medications/" + id, other.access(), null, Map.of()).status()).isEqualTo(404);
        var from = java.time.Instant.now().plusSeconds(120).atOffset(java.time.ZoneOffset.UTC).withNano(0);
        var slot = Map.of("localTime", "12:00", "daysOfWeek", List.of("MONDAY", "TUESDAY", "WEDNESDAY",
                "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"), "dose", 1, "unit", "TABLET");
        var schedule = call("POST", "/medications/" + id + "/schedule-versions", owner.access(),
                Map.of("effectiveFrom", from.toString(), "timeZone", "UTC", "slots", List.of(slot)),
                Map.of("If-Match", etag(created), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(schedule.status()).isEqualTo(201);
        var next = call("POST", "/medications/" + id + "/schedule-versions", owner.access(),
                Map.of("effectiveFrom", from.plusDays(1).toString(), "timeZone", "UTC", "slots", List.of(slot)),
                Map.of("If-Match", etag(schedule), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(next.status()).isEqualTo(201);
        assertThat(call("GET", "/medications/" + id + "/schedule-versions", owner.access(), null, Map.of()).body())
                .hasSize(2);
        String date = from.plusDays(2).toLocalDate().toString();
        var plan = call("GET",
                "/medication-occurrences?from=" + date + "&to=" + date + "&timeZone=UTC&medicationId=" + id,
                owner.access(), null, Map.of());
        assertThat(plan.status()).isEqualTo(200);
        var occurrence = plan.body().get("items").get(0);
        assertThat(occurrence.get("status").asString()).isEqualTo("NO_DATA");
        var mark = Map.of("kind", "PLANNED", "occurrenceId", occurrence.get("occurrenceId").asString(), "status",
                "SKIPPED");
        var key = key(UUID.randomUUID().toString());
        var intake = call("POST", "/intakes", owner.access(), mark, key);
        assertThat(intake.status()).isEqualTo(201);
        assertThat(call("POST", "/intakes", owner.access(), mark, key).body()).isEqualTo(intake.body());
        assertThat(
                call("POST", "/intakes", owner.access(), mark, key(UUID.randomUUID().toString())).status()).isEqualTo(
                409);
        assertThat(
                call("POST", "/intakes", other.access(), mark, key(UUID.randomUUID().toString())).status()).isEqualTo(
                404);
        assertThat(
                call("GET", "/intakes/" + intake.body().get("id").asString(), other.access(), null, Map.of()).status())
                .isEqualTo(404);
        var archived = call("POST", "/medications/" + id + "/archive", owner.access(), null,
                Map.of("If-Match", etag(next), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(archived.status()).isEqualTo(200);
        assertThat(
                call("DELETE", "/medications/" + id, owner.access(), null, Map.of("If-Match", etag(archived))).status())
                .isEqualTo(204);
        assertThat(call("POST", "/intakes", owner.access(), mark, key).status()).isEqualTo(410);
        assertThat(jdbc.queryForObject("select count(*) from schedule_version where medication_id=?", Integer.class,
                UUID.fromString(id))).isZero();
    }


    @Test
    @DisplayName("Ручные теги проверяют UTF-16, историю ревью, поиск и инвалидацию после правки")
    void manualTagsAndRevisionInvalidation() throws Exception {
        var owner = register();
        var other = register();
        var created = call("POST", "/entries", owner.access(), entry("😀 сегодня тревожно"),
                key(UUID.randomUUID().toString()));
        String id = created.body().get("id").asString();
        var manual = Map.of("labelCode", "ANXIETY", "quote", "тревожно", "start", 11, "end", 19,
                "presence", "PRESENT", "temporal", "CURRENT", "subject", "SELF", "appliesToLocalDate", "2026-10-13");
        var request = Map.of("revision", 1, "complete", true, "decisions", List.of(), "manualTags", List.of(manual));
        var reviewed = call("PUT", "/entries/" + id + "/tag-review", owner.access(), request,
                Map.of("If-Match", etag(created), "Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(reviewed.status()).isEqualTo(200);
        assertThat(reviewed.body().get("reviewComplete").asBoolean()).isTrue();
        assertThat(reviewed.body().get("tags").get(0).get("start").asInt()).isEqualTo(11);
        assertThat(
                call("GET", "/entries?confirmedTag=ANXIETY", owner.access(), null, Map.of()).body().get("items").size())
                .isEqualTo(1);
        assertThat(call("PUT", "/entries/" + id + "/tag-review", other.access(), request,
                Map.of("If-Match", etag(reviewed), "Idempotency-Key",
                        UUID.randomUUID().toString())).status()).isEqualTo(404);
        var edited = call("PATCH", "/entries/" + id, owner.access(), Map.of("text", "другой текст"),
                Map.of("If-Match", etag(reviewed)));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(edited.body().get("tags").size()).isZero();
        assertThat(edited.body().get("reviewComplete").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from entry_tag where entry_id=?", Integer.class,
                UUID.fromString(id)))
                .isEqualTo(1);
        assertThat(call("GET", "/entries?confirmedTag=ANXIETY", owner.access(), null, Map.of()).body().get(
                "items").size()).isZero();
        assertThat(call("DELETE", "/entries/" + id, owner.access(), null,
                Map.of("If-Match", etag(edited))).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from entry_tag where entry_id=?", Integer.class,
                UUID.fromString(id)))
                .isZero();
    }


    @Test
    @DisplayName("Календарь и обзор согласованы, пустые даты и данные другого аккаунта не подменяют наблюдения")
    void calendarAndAnalytics() throws Exception {
        var owner = register();
        var other = register();
        assertThat(call("PUT", "/ratings/2026-10-13/MORNING", owner.access(),
                rating(0, null, 10), key(UUID.randomUUID().toString())).status()).isEqualTo(201);
        assertThat(call("PUT", "/ratings/2026-10-13/EVENING", owner.access(),
                rating(10, 6, null), key(UUID.randomUUID().toString())).status()).isEqualTo(201);
        String period = "?from=2026-10-13&to=2026-10-14&timeZone=UTC";
        var calendar = call("GET", "/calendar" + period, owner.access(), null, Map.of());
        assertThat(calendar.status()).isEqualTo(200);
        assertThat(calendar.body().get("days").size()).isEqualTo(2);
        assertThat(calendar.body().get("days").get(0).get("ratings").get("DAY").isNull()).isTrue();
        var overview = call("GET", "/analytics/overview" + period, owner.access(), null, Map.of());
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.body().get("dailyValues").get(0).get("anxietyMean").asDouble()).isEqualTo(5);
        assertThat(overview.body().get("dailyValues").get(1).get("anxietyMean").isNull()).isTrue();
        assertThat(overview.body().get("coverage").get("metrics").get("ANXIETY").get("filled").asInt()).isEqualTo(2);
        assertThat(call("GET", "/analytics/overview" + period, other.access(), null, Map.of()).body()
                .get("coverage").get("filledSlots").asInt()).isZero();
        var comparison = call("GET", "/analytics/tag-comparisons" + period + "&tagCode=ANXIETY&metric=ANXIETY",
                owner.access(), null, Map.of());
        assertThat(comparison.status()).isEqualTo(200);
        assertThat(comparison.body().get("excluded").get("noEntry").asInt()).isEqualTo(1);
        assertThat(call("GET", "/days/2026-10-13?timeZone=UTC", owner.access(), null, Map.of()).status()).isEqualTo(
                200);
    }

    @Test
    @DisplayName("Отчёт исключает скрытые записи, проверяет владельца и блокирует устаревший PDF")
    void reportSelectionAndStaleness() throws Exception {
        var owner = register();
        var other = register();
        var visible = call("POST", "/entries", owner.access(), entry("видимый синтетический текст"),
                key(UUID.randomUUID().toString()));
        var hiddenRequest = new HashMap<>(entry("скрытый синтетический маркер"));
        hiddenRequest.put("reportVisible", false);
        call("POST", "/entries", owner.access(), hiddenRequest, key(UUID.randomUUID().toString()));
        var request = Map.of("from", "2026-10-13", "to", "2026-10-13", "timeZone", "UTC",
                "sections", List.of("RATINGS", "NOTES"), "entrySelection", "ALL_VISIBLE",
                "medicationSelection", "NONE");
        var report = call("POST", "/reports", owner.access(), request, key(UUID.randomUUID().toString()));
        assertThat(report.status()).isEqualTo(202);
        assertThat(report.body().get("previewData").get("entries")).hasSize(1);
        assertThat(report.body().toString()).doesNotContain("скрытый синтетический маркер");
        String id = report.body().get("id").asString();
        assertThat(call("GET", "/reports/" + id, other.access(), null, Map.of()).status()).isEqualTo(404);
        call("PATCH", "/entries/" + visible.body().get("id").asString(), owner.access(),
                Map.of("text", "исправленный синтетический текст"), Map.of("If-Match", etag(visible)));
        var stale = call("GET", "/reports/" + id, owner.access(), null, Map.of());
        assertThat(stale.body().get("stale").asBoolean()).isTrue();
        assertThat(stale.body().get("previewData").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select snapshot_json is null from report where id=?", Boolean.class,
                UUID.fromString(id))).isTrue();
        assertThat(call("GET", "/reports/" + id + "/pdf", owner.access(), null, Map.of()).status()).isEqualTo(409);
    }

    @Test
    @DisplayName("Готовый отчёт удаляется по актуальной версии и не ломает остальные отчёты")
    void readyReportDeletionKeepsOtherReportsIntact() throws Exception {
        var owner = register();
        var request = Map.of("from", "2026-10-13", "to", "2026-10-13", "timeZone", "UTC",
                "sections", List.of("RATINGS"), "entrySelection", "NONE", "medicationSelection", "NONE");
        var first = call("POST", "/reports", owner.access(), request, key(UUID.randomUUID().toString()));
        var second = call("POST", "/reports", owner.access(), request, key(UUID.randomUUID().toString()));
        String firstId = first.body().get("id").asString();
        String secondId = second.body().get("id").asString();
        assertThat(first.body().get("status").asString()).isEqualTo("READY");

        assertThat(call("DELETE", "/reports/" + firstId, owner.access(), null, Map.of("If-Match", etag(first)))
                .status()).isEqualTo(204);

        assertThat(call("GET", "/reports/" + firstId, owner.access(), null, Map.of()).status()).isEqualTo(404);
        var remaining = call("GET", "/reports/" + secondId, owner.access(), null, Map.of());
        assertThat(remaining.status()).isEqualTo(200);
        assertThat(remaining.body().get("stale").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select snapshot_json is not null from report where id=?", Boolean.class,
                UUID.fromString(secondId))).isTrue();
    }

    @Test
    @DisplayName("ZIP содержит историю владельца; правки очищают старые артефакты, удаление аккаунта отзывает токены")
    void fullExportAndAccountDeletion() throws Exception {
        var owner = register();
        var other = register();
        var created = call("POST", "/entries", owner.access(), entry("Синтетический экспортный маркер"),
                key(UUID.randomUUID().toString()));
        var export = call("POST", "/data-exports", owner.access(), null, key(UUID.randomUUID().toString()));
        assertThat(export.status()).as(export.body().toString()).isEqualTo(202);
        String id = export.body().get("id").asString();
        assertThat(call("GET", "/data-exports/" + id, other.access(), null, Map.of()).status()).isEqualTo(404);
        var request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/data-exports/" + id + "/file"))
                .header("Authorization", "Bearer " + owner.access()).GET().build();
        var file = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.headers().firstValue("Cache-Control")).contains("no-store");
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(file.body()))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("diary.json");
            String payload = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(payload).contains("Синтетический экспортный маркер", "entry_revision").doesNotContain(
                    "password_hash", "refresh_token");
        }
        call("PATCH", "/entries/" + created.body().get("id").asString(), owner.access(),
                Map.of("text", "изменённый синтетический текст"), Map.of("If-Match", etag(created)));
        assertThat(call("GET", "/data-exports/" + id + "/file", owner.access(), null, Map.of()).status()).isEqualTo(
                410);
        assertThat(jdbc.queryForObject("select archive is null from data_export where id=?", Boolean.class,
                UUID.fromString(id))).isTrue();
        assertThat(call("DELETE", "/me", owner.access(), Map.of("confirm", true, "currentPassword", "wrong"),
                Map.of()).status()).isEqualTo(403);
        var profile = call("GET", "/me", owner.access(), null, Map.of());
        UUID userId = UUID.fromString(profile.body().get("id").asString());
        assertThat(
                call("DELETE", "/me", owner.access(), Map.of("confirm", true, "currentPassword", "demo-password-1234"),
                        Map.of()).status()).isEqualTo(204);
        assertThat(call("GET", "/me", owner.access(), null, Map.of()).status()).isEqualTo(401);
        for (String table : List.of("diary_entry", "data_export", "auth_session", "mutation_receipt")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Integer.class,
                    userId)).isZero();
        }
    }

    @Test
    @DisplayName("AI-кандидат подтверждается человеком; чужой доступ и поздний ответ после правки блокируются")
    void extractionPipelineAndLateAnswer() throws Exception {
        controlledModel.invalid = false;
        var owner = register();
        var other = register();
        enableAi(owner);
        var created = call("POST", "/entries", owner.access(), entry("😀 сегодня тревожно"),
                key(UUID.randomUUID().toString()));
        String entryId = created.body().get("id").asString();
        String base = "/entries/" + entryId;
        var analysis = call("POST", base + "/analyses", owner.access(), Map.of("revision", 1),
                key(UUID.randomUUID().toString()));
        assertThat(analysis.status()).as(analysis.body().toString()).isEqualTo(202);
        String taskPath = base + "/analyses/" + analysis.body().get("id").asString();
        assertThat(call("GET", taskPath, other.access(), null, Map.of()).status()).isEqualTo(404);
        aiTick();
        var ready = call("GET", taskPath, owner.access(), null, Map.of());
        assertThat(ready.body().get("status").asString()).isEqualTo("SUCCEEDED");
        var candidate = ready.body().get("result").get("candidates").get(0);
        assertThat(candidate.get("start").asInt()).isEqualTo(11);
        var reviewed = call("PUT", base + "/tag-review", owner.access(), Map.of("revision", 1, "complete", true,
                        "decisions", List.of(Map.of("candidateId", candidate.get("candidateId").asString(),
                                "action", "ACCEPT", "appliesToLocalDate", "2026-10-13")), "manualTags", List.of()),
                Map.of("Idempotency-Key", UUID.randomUUID().toString(), "If-Match", etag(created)));
        assertThat(reviewed.status()).as(reviewed.body().toString()).isEqualTo(200);
        assertThat(reviewed.body().get("tags").get(0).get("origin").asString()).isEqualTo("AI");
        assertThat(jdbc.queryForObject("select count(*) from review_candidate_decision where entry_id=?", Integer.class,
                UUID.fromString(entryId))).isEqualTo(1);
        var changed = call("PATCH", base, owner.access(), Map.of("text", "тревожно после правки"),
                Map.of("If-Match", etag(reviewed)));
        var next = call("POST", base + "/analyses", owner.access(), Map.of("revision", 2),
                key(UUID.randomUUID().toString()));
        assertThat(next.status()).isEqualTo(202);
        var claimed = aiQueue.claim();
        assertThat(claimed).isNotNull();
        call("PATCH", base, owner.access(), Map.of("text", "новая редакция без признаков"),
                Map.of("If-Match", etag(changed)));
        aiQueue.succeed(claimed, aiValidator.extraction("тревожно после правки", controlledModel.generate(
                new ModelRequest(AiKind.ENTRY, "test", "{}", "entry-candidates-v1"))), null);
        var stale = call("GET", base + "/analyses/" + next.body().get("id").asString(), owner.access(), null, Map.of());
        assertThat(stale.body().get("status").asString()).isEqualTo("STALE");
        assertThat(stale.body().get("result").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from analysis_candidate where task_id=?", Integer.class,
                UUID.fromString(next.body().get("id").asString()))).isZero();
        assertThat(call("DELETE", base, owner.access(), null,
                Map.of("If-Match", etag(call("GET", base, owner.access(), null, Map.of())))).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from ai_task where entry_id=?", Integer.class,
                UUID.fromString(entryId))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from analysis_candidate where entry_id=?", Integer.class,
                UUID.fromString(entryId))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from review_candidate_decision where entry_id=?", Integer.class,
                UUID.fromString(entryId))).isZero();
    }

    @Test
    @DisplayName("Обзор по коротким идентификаторам фактов проходит проверку и экспортируется в PDF; два невалидных ответа дают честный шаблон")
    void overviewPipelineAndFallback() throws Exception {
        controlledModel.invalid = false;
        var owner = register();
        enableAi(owner);
        var request = Map.of("from", "2026-10-13", "to", "2026-10-13", "timeZone", "UTC",
                "sections", List.of("RATINGS", "AI_OVERVIEW"), "entrySelection", "NONE", "medicationSelection", "NONE");
        var created = call("POST", "/reports", owner.access(), request, key(UUID.randomUUID().toString()));
        assertThat(created.status()).as(created.body().toString()).isEqualTo(202);
        assertThat(created.body().get("status").asString()).isEqualTo("QUEUED");
        String path = "/reports/" + created.body().get("id").asString();
        assertThat(call("GET", path + "/pdf", owner.access(), null, Map.of()).status()).isEqualTo(409);
        aiTick();
        var ready = call("GET", path, owner.access(), null, Map.of());
        assertThat(ready.body().get("status").asString()).isEqualTo("READY");
        assertThat(ready.body().get("overviewMode").asString()).isEqualTo("AI");
        var fileRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path + "/pdf"))
                .header("Authorization", "Bearer " + owner.access()).GET().build();
        var pdf = http.send(fileRequest, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(new String(pdf.body(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        controlledModel.invalid = true;
        try {
            var second = call("POST", "/reports", owner.access(), request, key(UUID.randomUUID().toString()));
            aiTick();
            aiTick();
            var fallback = call("GET", "/reports/" + second.body().get("id").asString(), owner.access(), null,
                    Map.of());
            assertThat(fallback.body().get("status").asString()).isEqualTo("READY");
            assertThat(fallback.body().get("overviewMode").asString()).isEqualTo("TEMPLATE");
            assertThat(fallback.body().get("warnings").toString()).contains("INVALID_MODEL_RESULT");
        } finally {
            controlledModel.invalid = false;
        }
    }

}
