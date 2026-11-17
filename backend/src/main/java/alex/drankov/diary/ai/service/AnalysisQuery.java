package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.dto.AnalysisResponse;
import alex.drankov.diary.ai.dto.ExtractionResultResponse;
import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.repository.EntryRepository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisQuery {
    private final AiTaskRepository tasks;
    private final AnalysisCandidateRepository candidates;
    private final EntryRepository entries;

    public AnalysisResponse latest(DiaryEntry entry) {
        return tasks.findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(entry.getUserId(), entry.getId(), entry.getRevision())
                .map(task -> response(task, entry.getRevision())).orElse(null);
    }

    public AnalysisResponse get(
            UUID user,
            UUID entryId,
            UUID id
    ) {
        var entry = entries.findByIdAndUserId(entryId, user).orElseThrow(ApiException::notFound);
        var task = tasks.findByIdAndUserId(id, user).filter(value -> entryId.equals(value.getEntryId())).orElseThrow(ApiException::notFound);
        return response(task, entry.getRevision());
    }

    public AnalysisResponse response(
            AiTask task,
            int currentRevision
    ) {
        var status = task.getRevision() != currentRevision ? AiStatus.STALE : task.getStatus();
        var result = status == AiStatus.SUCCEEDED
                ? new ExtractionResultResponse(candidates.findByTaskIdOrderByStartIndexAscIdAsc(task.getId()).stream()
                .map(value -> value.response()).toList()) : null;
        return new AnalysisResponse(task.getId(), task.getEntryId(), task.getRevision(), status, task.getAttempt(),
                task.getCreatedAt(), task.getFinishedAt(), task.getErrorCode(), result, task.getModelId(),
                task.getPromptVersion(), task.getSchemaVersion());
    }
}
