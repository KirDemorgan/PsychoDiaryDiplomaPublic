package alex.drankov.diary.speech.repository;

import alex.drankov.diary.speech.entity.Transcription;
import alex.drankov.diary.speech.entity.TranscriptionStatus;
import alex.drankov.diary.speech.service.model.TranscriptionIdentity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TranscriptionRepository extends JpaRepository<Transcription, UUID> {
    Optional<Transcription> findByIdAndUserId(UUID id, UUID user);

    boolean existsByUserIdAndStatusIn(UUID user, Collection<TranscriptionStatus> statuses);

    long countByUserIdAndCreatedAtAfter(UUID user, Instant after);

    @Query("select new alex.drankov.diary.speech.service.model.TranscriptionIdentity(t.id,t.userId) from Transcription t where t.id=:id")
    Optional<TranscriptionIdentity> identity(UUID id);

    @Query("select new alex.drankov.diary.speech.service.model.TranscriptionIdentity(t.id,t.userId) from Transcription t where t.status='QUEUED' or t.status='RUNNING' and t.leaseUntil<=:now order by t.createdAt,t.id")
    List<TranscriptionIdentity> runnable(Instant now, Pageable page);

    @Query("select new alex.drankov.diary.speech.service.model.TranscriptionIdentity(t.id,t.userId) from Transcription t where t.expiresAt<=:now and t.status<>'EXPIRED' order by t.expiresAt,t.id")
    List<TranscriptionIdentity> expired(Instant now, Pageable page);

    @Query("select new alex.drankov.diary.speech.service.model.TranscriptionIdentity(t.id,t.userId) from Transcription t where t.expiresAt<=:before and t.status='EXPIRED' order by t.expiresAt,t.id")
    List<TranscriptionIdentity> obsolete(Instant before, Pageable page);
}
