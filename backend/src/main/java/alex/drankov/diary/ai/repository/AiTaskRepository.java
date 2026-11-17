package alex.drankov.diary.ai.repository;

import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.service.model.AiTaskIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {
    Optional<AiTask> findByIdAndUserId(UUID id, UUID user);

    Optional<AiTask> findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(UUID user, UUID entry, int revision);

    Optional<AiTask> findFirstByUserIdAndReportIdOrderByCreatedAtDescIdDesc(UUID user, UUID report);

    @Query("select new alex.drankov.diary.ai.service.model.AiTaskIdentity(t.id,t.userId) from AiTask t where t.id=:id")
    Optional<AiTaskIdentity> identity(UUID id);

    @Query("select new alex.drankov.diary.ai.service.model.AiTaskIdentity(t.id,t.userId) from AiTask t where t.status='QUEUED' or t.status='RUNNING' and t.leaseUntil<=:now order by t.createdAt,t.id")
    List<AiTaskIdentity> runnable(Instant now, Pageable page);
}
