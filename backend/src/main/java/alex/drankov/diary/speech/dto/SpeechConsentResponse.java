package alex.drankov.diary.speech.dto;

import java.time.Instant;

public record SpeechConsentResponse(long version, boolean enabled, String consentVersion, Instant acceptedAt,
                                    String currentVersion) {
}
