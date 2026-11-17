package alex.drankov.diary.ai.adapter;

import alex.drankov.diary.ai.entity.AiKind;

public record ModelRequest(
        AiKind kind,
        String systemPrompt,
        String inputJson,
        String schemaVersion
) {
    @Override
    public String toString() {
        return "ModelRequest[kind=" + kind + ", schemaVersion=" + schemaVersion + "]";
    }
}
