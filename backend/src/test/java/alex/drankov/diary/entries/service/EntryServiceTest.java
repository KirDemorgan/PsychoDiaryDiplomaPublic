package alex.drankov.diary.entries.service;

import alex.drankov.diary.ai.service.AnalysisQuery;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.dto.CreateEntryRequest;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.dto.UpdateEntryRequest;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.entries.repository.EntryRevisionRepository;
import alex.drankov.diary.tags.service.TagQuery;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static alex.drankov.diary.tags.entity.TagCode.ANXIETY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {
    @Mock
    EntryRepository entries;
    @Mock
    EntryRevisionRepository revisions;
    @Mock
    UserAccess access;
    @Mock
    Idempotency idempotency;
    @Mock
    TagQuery tags;
    EntryService service;
    User owner;
    DiaryEntry entry;
    JsonMapper json = new JsonMapper();

    @BeforeEach
    void setup() {
        owner = user();
        entry = persisted(new DiaryEntry(owner.getId(), "old", NOW, "UTC", true));
        service = new EntryService(entries, revisions, Mappers.getMapper(EntryMapper.class), access, idempotency, tags, Mockito.mock(AnalysisQuery.class));
    }

    @Test
    @DisplayName("Сохранение создаёт запись и редакцию независимо от AI")
    void saveCreatesTextAndRevisionBeforeAnyAi() {
        var request = new CreateEntryRequest("new", NOW.atOffset(ZoneOffset.UTC), "UTC", null);
        when(access.lock(owner.getId())).thenReturn(owner);
        when(idempotency.execute(eq(owner.getId()), anyString(), any(), eq(request), eq(EntryResponse.class), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
        when(entries.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        var result = service.create(owner.getId(), UUID.randomUUID(), request);
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().text()).isEqualTo("new");
        assertThat(result.body().reportVisible()).isTrue();
        assertThat(result.body().latestAnalysis()).isNull();
        var order = inOrder(entries, revisions);
        order.verify(entries).saveAndFlush(any());
        order.verify(revisions).saveAndFlush(any());
        assertThat(owner.getDataRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Правка текста создаёт редакцию, изменение видимости отчёта сохраняет прежнюю")
    void textEditCreatesRevisionButVisibilityOnlyDoesNot() {
        when(access.lock(owner.getId())).thenReturn(owner);
        when(entries.findByIdAndUserId(entry.getId(), owner.getId())).thenReturn(Optional.of(entry));
        var response = service.patch(owner.getId(), entry.getId(), "\"v0\"",
                json.readValue("{\"text\":\"new\"}", UpdateEntryRequest.class));
        assertThat(response.revision()).isEqualTo(2);
        assertThat(response.reviewComplete()).isFalse();
        verify(revisions).save(any());
        clearInvocations(revisions);
        service.patch(owner.getId(), entry.getId(), "\"v0\"",
                json.readValue("{\"reportVisible\":false}", UpdateEntryRequest.class));
        assertThat(entry.isReportVisible()).isFalse();
        assertThat(entry.getRevision()).isEqualTo(2);
        verifyNoInteractions(revisions);
    }

    @Test
    @DisplayName("Правка даты или зоны создаёт редакцию и проверяет смещение")
    void changingTimeContextCreatesRevisionAndOffsetIsChecked() {
        when(access.lock(owner.getId())).thenReturn(owner);
        when(entries.findByIdAndUserId(entry.getId(), owner.getId())).thenReturn(Optional.of(entry));
        service.patch(owner.getId(), entry.getId(), "\"v0\"",
                json.readValue("{\"timeZone\":\"Asia/Yekaterinburg\",\"occurredAt\":\"2026-10-13T16:00:00+05:00\"}",
                        UpdateEntryRequest.class));
        assertThat(entry.getOccurredAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(entry.getRevision()).isEqualTo(2);
        assertThatThrownBy(() -> service.patch(owner.getId(), entry.getId(), "\"v0\"",
                json.readValue("{\"occurredAt\":\"wrong\"}", UpdateEntryRequest.class))).isInstanceOf(
                tools.jackson.core.JacksonException.class);
    }

    @Test
    @DisplayName("Другой пользователь не может прочитать или удалить запись")
    void anotherOwnerCannotReadOrDeleteEntry() {
        UUID other = UUID.randomUUID();
        when(entries.findByIdAndUserId(entry.getId(), other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(other, entry.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThatThrownBy(() -> service.delete(other, entry.getId(), "\"v0\"")).isInstanceOf(ApiException.class);
        verify(entries, never()).delete(any());
    }

    @Test
    @DisplayName("Устаревшая версия не меняет запись, удаление очищает сохранённый ответ повтора")
    void staleVersionCannotMutateAndDeletionScrubsReplay() {
        when(access.lock(owner.getId())).thenReturn(owner);
        when(entries.findByIdAndUserId(entry.getId(), owner.getId())).thenReturn(Optional.of(entry));
        assertThatThrownBy(() -> service.patch(owner.getId(), entry.getId(), "\"v1\"",
                json.readValue("{\"text\":\"new\"}", UpdateEntryRequest.class))).isInstanceOf(ApiException.class);
        assertThat(entry.getText()).isEqualTo("old");
        service.delete(owner.getId(), entry.getId(), "\"v0\"");
        var order = inOrder(idempotency, entries);
        order.verify(idempotency).erase(owner.getId(), entry.getId());
        order.verify(entries).delete(entry);
    }

    @Test
    @DisplayName("Список записей учитывает обе граничные даты и стабильный порядок")
    void listingUsesInclusiveLocalDatesAndStableOrder() {
        var from = LocalDate.of(2026, 10, 13);
        when(entries.list(eq(owner.getId()), any(), any(), anyString(), any())).thenReturn(
                new PageImpl<>(List.of(entry)));
        var result = service.list(owner.getId(), from, from, "Asia/Yekaterinburg", "a%_!", 0, 20);
        assertThat(result.items()).hasSize(1);
        verify(entries).list(eq(owner.getId()), eq(Instant.parse("2026-10-12T19:00:00Z")),
                eq(Instant.parse("2026-10-13T19:00:00Z")), eq("%a!%!_!!%"), any());
        assertThatThrownBy(() -> service.list(owner.getId(), from, null, "UTC", null, 0, 20)).isInstanceOf(
                ApiException.class);
    }

    @Test
    @DisplayName("Список без периода использует зону пользователя и сохраняет emoji в превью")
    void defaultZoneAndNoDateFilterWorkWithoutSplittingEmoji() {
        when(access.read(owner.getId())).thenReturn(owner);
        when(entries.findByUserIdAndTextContainingIgnoreCase(eq(owner.getId()), eq(""), any())).thenReturn(
                Page.empty());
        assertThat(service.list(owner.getId(), null, null, null, null, 0, 20).items()).isEmpty();
        var longEntry = persisted(new DiaryEntry(owner.getId(), "😀".repeat(200), NOW, "UTC", true));
        var summary = Mappers.getMapper(EntryMapper.class).summary(longEntry, ZoneOffset.UTC);
        assertThat(summary.textPreview().codePointCount(0, summary.textPreview().length())).isEqualTo(160);
        assertThatThrownBy(() -> service.list(owner.getId(), null, null, "UTC", "a".repeat(201), 0, 20)).isInstanceOf(
                ApiException.class);
    }

    @Test
    @DisplayName("Фильтр присутствующего тега сохраняет период, буквальный поиск и пагинацию")
    void confirmedTagFilter() {
        var day = LocalDate.of(2026, 10, 13);
        when(entries.listTagged(eq(owner.getId()), any(), any(), eq("%a!%%"),
                eq(ANXIETY), any())).thenReturn(new PageImpl<>(List.of(entry)));
        when(tags.observedCodes(entry)).thenReturn(List.of("ANXIETY"));
        var result = service.list(owner.getId(), day, day, "UTC", "a%",
                ANXIETY, 0, 20);
        assertThat(result.items().getFirst().confirmedTags()).containsExactly("ANXIETY");
        when(access.read(owner.getId())).thenReturn(owner);
        when(entries.listTagged(eq(owner.getId()), any(), any(), eq("%%"),
                eq(ANXIETY), any())).thenReturn(Page.empty());
        assertThat(service.list(owner.getId(), null, null, null, null,
                ANXIETY, 0, 20).items()).isEmpty();
        assertThatThrownBy(() -> service.list(owner.getId(), day, null, "UTC", null,
                ANXIETY, 0, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(owner.getId(), null, null, "UTC", "a".repeat(201),
                ANXIETY, 0, 20)).isInstanceOf(ApiException.class);
    }

}
