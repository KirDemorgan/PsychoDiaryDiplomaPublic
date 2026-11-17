package alex.drankov.diary.speech.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SpeechConsentRequest(@NotNull Boolean enabled, @Size(max = 100) String consentVersion) {
}
