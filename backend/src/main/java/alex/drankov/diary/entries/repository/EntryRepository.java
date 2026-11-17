package alex.drankov.diary.entries.repository;

import alex.drankov.diary.entries.entity.DiaryEntry;

import alex.drankov.diary.tags.entity.TagCode;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<DiaryEntry, UUID> {
    @Query("select e from DiaryEntry e where e.userId=:user and e.occurredAt>=:from and e.occurredAt<:until order by e.occurredAt,e.id")
    List<DiaryEntry> inPeriod(UUID user, Instant from, Instant until);

    List<DiaryEntry> findByUserIdOrderByOccurredAtAscIdAsc(UUID userId);

    Optional<DiaryEntry> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select e from DiaryEntry e where e.userId=:user
            and e.occurredAt >= :from and e.occurredAt < :until
            and lower(e.text) like :query escape '!'
            """)
    Page<DiaryEntry> list(UUID user, Instant from, Instant until, String query, Pageable page);

    @Query("""
            select e from DiaryEntry e where e.userId=:user and e.occurredAt>=:from and e.occurredAt<:until
            and lower(e.text) like :query escape '!'
            and exists (select t.id from EntryTag t where t.reviewId=e.currentReviewId and t.labelCode=:code
            and t.presence=alex.drankov.diary.tags.entity.TagPresence.PRESENT
            and t.subject=alex.drankov.diary.tags.entity.TagSubject.SELF
            and t.temporal<>alex.drankov.diary.tags.entity.TagTemporal.FUTURE and t.appliesToLocalDate is not null)
            """)
    Page<DiaryEntry> listTagged(UUID user, Instant from, Instant until, String query, TagCode code, Pageable page);

    Page<DiaryEntry> findByUserIdAndTextContainingIgnoreCase(UUID user, String text, Pageable page);
}
