package alex.drankov.diary.tags.repository;

import alex.drankov.diary.tags.entity.EntryTag;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryTagRepository extends JpaRepository<EntryTag, UUID> {
    List<EntryTag> findByReviewIdOrderByStartAscIdAsc(UUID reviewId);

    @Query("select t from EntryTag t, DiaryEntry e where t.entryId=e.id and t.reviewId=e.currentReviewId and e.userId=:user order by t.start,t.id")
    List<EntryTag> currentForUser(UUID user);

    @Query("select t from EntryTag t, DiaryEntry e where t.entryId=e.id and e.userId=:user order by t.createdAt,t.id")
    List<EntryTag> historyForUser(UUID user);
}
