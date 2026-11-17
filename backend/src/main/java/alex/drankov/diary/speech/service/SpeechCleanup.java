package alex.drankov.diary.speech.service;

import alex.drankov.diary.speech.repository.TranscriptionRepository;

import java.time.Clock;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeechCleanup {
    private final TranscriptionRepository tasks;
    private final TranscriptionService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.speech.cleanup-delay:PT1M}")
    public void tick() {
        var page = PageRequest.of(0, 100);
        tasks.expired(clock.instant(), page).forEach(service::expire);
        tasks.obsolete(clock.instant().minusSeconds(7 * 86400), page).forEach(service::expire);
    }
}
