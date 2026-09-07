package com.ontotrace.runcontrol;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理运行仓储。jsonb 列通过显式 CAST 单独更新。
 *
 * @author hanbd
 */
public interface ProcessingRunRepository extends ListCrudRepository<ProcessingRun, UUID> {

    /**
     * 按任务读取运行。
     *
     * @param jobId 任务标识
     * @return 处理运行
     */
    Optional<ProcessingRun> findFirstByJobIdOrderByCreatedAtDesc(UUID jobId);

    /**
     * 写入复核结果分布。
     *
     * @param id 运行标识
     * @param json 复核摘要 JSON
     */
    @Transactional
    @Modifying
    @Query("UPDATE processing_run SET review_result_summary = CAST(:json AS jsonb) WHERE id = :id")
    void setReviewSummary(UUID id, String json);

    /**
     * 写入运行参数（附件前缀、丢弃数量等）。
     *
     * @param id 运行标识
     * @param json 参数 JSON
     */
    @Transactional
    @Modifying
    @Query("UPDATE processing_run SET parameters = CAST(:json AS jsonb) WHERE id = :id")
    void setParameters(UUID id, String json);
}
