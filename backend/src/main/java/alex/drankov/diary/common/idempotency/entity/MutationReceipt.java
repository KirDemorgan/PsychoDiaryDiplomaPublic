package alex.drankov.diary.common.idempotency.entity;

import alex.drankov.diary.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mutation_receipt", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "route", "operation_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MutationReceipt extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String route;

    @Column(nullable = false)
    private UUID operationId;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private long resourceVersion;

    @Column(nullable = false)
    private int responseStatus;

    @Column(columnDefinition = "text")
    private String responsePayload;

    @Column(nullable = false)
    private boolean gone;

    public MutationReceipt(
            UUID user,
            String route,
            UUID operation,
            String hash,
            UUID resource,
            long version,
            int status,
            String payload
    ) {
        userId = user;
        this.route = route;
        operationId = operation;
        requestHash = hash;
        resourceId = resource;
        resourceVersion = version;
        responseStatus = status;
        responsePayload = payload;
    }

    public void erase() {
        gone = true;
        responsePayload = null;
    }
}
