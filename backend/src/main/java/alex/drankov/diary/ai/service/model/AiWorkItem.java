package alex.drankov.diary.ai.service.model;

import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.reports.dto.FactResponse;

import java.util.List;
import java.util.UUID;

public record AiWorkItem(
        UUID taskId,
        UUID leaseToken,
        AiKind kind,
        String inputJson,
        String text,
        List<FactResponse> facts,
        String modelId,
        String promptVersion,
        String schemaVersion
) {
    @Override
    public String toString() {
        return "AiWorkItem[taskId=" + taskId + ", kind=" + kind + "]";
    }
}
