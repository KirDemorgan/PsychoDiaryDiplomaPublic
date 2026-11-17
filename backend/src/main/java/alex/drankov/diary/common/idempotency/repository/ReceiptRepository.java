package alex.drankov.diary.common.idempotency.repository;

import alex.drankov.diary.common.idempotency.entity.MutationReceipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<MutationReceipt, UUID> {
    Optional<MutationReceipt> findByUserIdAndRouteAndOperationId(UUID userId, String route, UUID operationId);

    List<MutationReceipt> findByUserIdAndResourceId(UUID userId, UUID resourceId);
}
