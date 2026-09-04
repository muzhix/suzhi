package com.ontotrace.runcontrol;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 任务仓储。领取到期任务使用 {@code FOR UPDATE SKIP LOCKED}。
 *
 * @author hanbd
 */
public interface JobRepository extends ListCrudRepository<Job, UUID> {

    /**
     * 按幂等键查找。
     *
     * @param idempotencyKey 幂等键
     * @return 任务
     */
    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    /**
     * 锁定一条可执行任务。必须在事务中调用。
     * 租约过期的 running 任务可被其它进程收回，但不抢本 worker 仍在执行的行。
     *
     * @param now 当前时间
     * @param workerId 本进程工作标识
     * @return 任务
     */
    @Query(
            """
            SELECT id, type, status, stage, progress, total, attempt_count, next_run_at, lease_until,
                   worker_id, idempotency_key, document_id, document_version_id, target_text_unit_id,
                   created_by, error_summary, created_at, updated_at
            FROM job
            WHERE next_run_at <= :now
              AND (
                status = 'pending'
                OR (status = 'running' AND lease_until < :now
                    AND (worker_id IS NULL OR worker_id <> :workerId))
              )
            ORDER BY next_run_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    Optional<Job> lockNext(Instant now, String workerId);

    /**
     * 为本进程在途任务续租。
     *
     * @param workerId 本进程工作标识
     * @param jobIds 在途任务
     * @param leaseUntil 新的租约到期时间
     * @param now 当前时间
     * @return 更新行数
     */
    @Modifying
    @Query(
            """
            UPDATE job
            SET lease_until = :leaseUntil, updated_at = :now
            WHERE status = 'running'
              AND worker_id = :workerId
              AND id IN (:jobIds)
            """)
    int renewLeases(String workerId, Collection<UUID> jobIds, Instant leaseUntil, Instant now);
}
