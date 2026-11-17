package alex.drankov.diary.speech.service.model;

import java.util.UUID;

public record SpeechWorkItem(UUID id, UUID leaseToken, byte[] audio, String modelId) {
    @Override
    public String toString() {
        return "SpeechWorkItem[id=" + id + ", audio=<redacted>]";
    }
}
