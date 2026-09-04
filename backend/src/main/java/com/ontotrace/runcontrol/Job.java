package com.ontotrace.runcontrol;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 后台任务。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("job")
public class Job implements Persistable<UUID> {

    @Id
    private UUID id;
    private String type;
    private String status;
    private String stage;
    private Integer progress;
    private Integer total;
    private Integer attemptCount;
    private Instant nextRunAt;
    private Instant leaseUntil;
    private String workerId;
    private String idempotencyKey;
    private UUID documentId;
    private UUID documentVersionId;
    private UUID targetTextUnitId;
    private UUID createdBy;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;
    @Transient
    @Builder.Default
    private boolean isNew = true;

    /**
     * 是否尚未插入。
     *
     * @return 新记录返回 true
     */
    @Override
    public boolean isNew() {
        return isNew;
    }
}
