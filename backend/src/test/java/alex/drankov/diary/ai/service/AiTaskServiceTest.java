package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.adapter.ModelAdapter;
import alex.drankov.diary.ai.dto.CreateAnalysisRequest;
import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static alex.drankov.diary.ai.entity.AiKind.ENTRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTaskServiceTest {
    @Test
    @DisplayName("Запуск сохраняет задачу, повтор использует текущую; отмена, чужой ID и завершённое ревью проверяются")
    void lifecycle() {
        var tasks = mock(AiTaskRepository.class);
        var entries = mock(EntryRepository.class);
        var candidates = mock(AnalysisCandidateRepository.class);
        var access = mock(UserAccess.class);
        var consent = mock(AiConsent.class);
        var idempotency = mock(Idempotency.class);
        var model = mock(ModelAdapter.class);
        var query = new AnalysisQuery(tasks, candidates, entries);
        var service = new AiTaskService(tasks, entries, access, consent, query, idempotency, model, CLOCK);
        var owner = user();
        var entry = persisted(new DiaryEntry(owner.getId(), "тревожно", NOW, "UTC", true));
        when(access.lock(owner.getId())).thenReturn(owner);
        when(entries.findByIdAndUserId(entry.getId(), owner.getId())).thenReturn(Optional.of(entry));
        when(tasks.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        when(model.modelId()).thenReturn("test-adapter");
        when(idempotency.execute(any(), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
        var result = service.start(owner.getId(), entry.getId(), UUID.randomUUID(), new CreateAnalysisRequest(1));
        assertThat(result.body().status()).isEqualTo(AiStatus.QUEUED);
        assertThat(result.resourceId()).isEqualTo(entry.getId());
        var task = new AiTask(owner.getId(), entry.getId(), null, 1, 0,
                AiTaskService.entryHash(entry), "test-adapter", ENTRY);
        persisted(task);
        when(tasks.findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(owner.getId(), entry.getId(), 1)).thenReturn(Optional.of(task));
        when(tasks.findByIdAndUserId(task.getId(), owner.getId())).thenReturn(Optional.of(task));
        assertThat(service.start(owner.getId(), entry.getId(), UUID.randomUUID(), new CreateAnalysisRequest(1)).body().id()).isEqualTo(task.getId());
        assertThat(query.latest(entry).status()).isEqualTo(AiStatus.QUEUED);
        assertThat(query.get(owner.getId(), entry.getId(), task.getId()).status()).isEqualTo(AiStatus.QUEUED);
        assertThat(service.cancel(owner.getId(), entry.getId(), task.getId(), UUID.randomUUID()).body().status()).isEqualTo(AiStatus.CANCELLED);
        task.finish(AiStatus.SUCCEEDED, null, NOW);
        assertThat(query.get(owner.getId(), entry.getId(), task.getId()).result().candidates()).isEmpty();
        assertThatThrownBy(() -> service.cancel(owner.getId(), entry.getId(), task.getId(), UUID.randomUUID())).isInstanceOf(ApiException.class);
        entry.revise("новый текст", NOW, "UTC");
        assertThat(query.get(owner.getId(), entry.getId(), task.getId()).status()).isEqualTo(AiStatus.STALE);
        assertThatThrownBy(() -> query.get(UUID.randomUUID(), entry.getId(), task.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.start(owner.getId(), entry.getId(), UUID.randomUUID(), new CreateAnalysisRequest(1))).hasMessageContaining("Редакция");
        entry.review(UUID.randomUUID(), true);
        assertThatThrownBy(() -> service.start(owner.getId(), entry.getId(), UUID.randomUUID(), new CreateAnalysisRequest(2))).hasMessageContaining("Проверенная");
    }
}
