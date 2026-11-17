package alex.drankov.diary.exports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.exports.dto.DeleteAccountRequest;
import alex.drankov.diary.exports.dto.ExportDocument;
import alex.drankov.diary.exports.entity.DataExport;
import alex.drankov.diary.exports.repository.DataExportRepository;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataManagementTest {
    @Test
    @DisplayName("Полный экспорт ограничен владельцем, сохраняет NULL и даты и исключает секретные таблицы")
    void collectionAndArchive() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var owner = user();
        var row = new HashMap<String, Object>();
        row.put("text", "Синтетическая скрытая запись");
        row.put("created_at", Timestamp.from(NOW));
        row.put("date", java.sql.Date.valueOf("2026-10-13"));
        row.put("time", java.sql.Time.valueOf("12:00:00"));
        row.put("mood", null);
        when(jdbc.queryForList(anyString(), eq(owner.getId()))).thenReturn(List.of(row));
        var document = new ExportCollector(jdbc).collect(owner.getId(), 5, NOW);
        assertThat(document.tables()).containsKeys("entry_revision", "tag_review", "entry_tag", "schedule_version", "report");
        assertThat(document.tables()).doesNotContainKeys("refresh_token", "auth_session", "mutation_receipt", "data_export");
        assertThat(document.tables().get("diary_entry").getFirst()).containsEntry("mood", null)
                .containsEntry("created_at", NOW.toString()).containsEntry("time", "12:00");
        byte[] bytes = new ExportArchive(JsonMapper.builder().build()).create(document);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("diary.json");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).contains("diary-export-v1", "Синтетическая скрытая запись");
            assertThat(zip.getNextEntry().getName()).isEqualTo("README.txt");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).contains("UTF-16", "Пароль, токены");
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    @DisplayName("Экспорт выдаётся 24 часа, чужие ID скрыты, истёкший файл недоступен и удаление стирает receipt")
    void exportLifecycle() {
        var repository = mock(DataExportRepository.class);
        var access = mock(UserAccess.class);
        var idempotency = mock(Idempotency.class);
        var collector = mock(ExportCollector.class);
        var archives = mock(ExportArchive.class);
        var owner = user();
        var document = new ExportDocument("diary-export-v1", NOW, 0, Map.of());
        when(access.lock(owner.getId())).thenReturn(owner);
        when(collector.collect(owner.getId(), 0, NOW)).thenReturn(document);
        when(archives.create(document)).thenReturn(new byte[]{1, 2});
        when(repository.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        when(idempotency.execute(any(), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
        var service = new DataExportService(repository, collector, archives, access, idempotency, CLOCK);
        var result = service.create(owner.getId(), UUID.randomUUID());
        assertThat(result.body().status()).isEqualTo("READY");
        assertThat(result.body().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        var available = persisted(new DataExport(owner.getId(), 0, NOW.plusSeconds(1), new byte[]{1, 2}));
        when(repository.findByIdAndUserId(available.getId(), owner.getId())).thenReturn(Optional.of(available));
        assertThat(service.file(owner.getId(), available.getId())).containsExactly((byte) 1, (byte) 2);
        assertThat(service.get(owner.getId(), available.getId()).status()).isEqualTo("READY");
        var expired = persisted(new DataExport(owner.getId(), 0, NOW, new byte[]{1}));
        when(repository.findByIdAndUserId(expired.getId(), owner.getId())).thenReturn(Optional.of(expired));
        assertThat(service.get(owner.getId(), expired.getId()).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.file(owner.getId(), expired.getId())).hasMessageContaining("истёк");
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), available.getId())).isInstanceOf(ApiException.class);
        service.delete(owner.getId(), available.getId());
        verify(idempotency).erase(owner.getId(), available.getId());
        verify(repository).delete(available);
        service.cleanup();
        verify(repository).eraseExpired(NOW);
    }

    @Test
    @DisplayName("Удаление аккаунта требует явного подтверждения и правильного пароля")
    void deleteAccount() {
        var access = mock(UserAccess.class);
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var owner = user();
        when(access.lock(owner.getId())).thenReturn(owner);
        var service = new AccountDeletionService(access, users, passwords);
        assertThatThrownBy(() -> service.delete(owner.getId(), new DeleteAccountRequest("password", false)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(owner.getId(), new DeleteAccountRequest("wrong", true)))
                .hasMessageContaining("пароль");
        verify(users, never()).delete(any());
        when(passwords.matches("password", owner.getPasswordHash())).thenReturn(true);
        service.delete(owner.getId(), new DeleteAccountRequest("password", true));
        verify(users).delete(owner);
        verify(users).flush();
        assertThat(new DeleteAccountRequest("secret", true).toString()).doesNotContain("secret");
    }
}
