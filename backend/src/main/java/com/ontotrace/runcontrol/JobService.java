package com.ontotrace.runcontrol;

import com.ontotrace.config.OntoTraceProperties;
import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务创建、查询、领取与执行。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class JobService {

    public static final String EXTRACT_CONTENT = "extract_content";
    public static final String EXTRACT_EDU = "extract_edu";

    private final JobRepository jobs;
    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final TextUnitRepository textUnits;
    private final OntoTraceProperties properties;
    private final Map<String, JobHandler> handlers;

    /**
     * 创建服务。
     *
     * @param jobs 任务仓储
     * @param documents 文档服务
     * @param versions 版本文仓
     * @param properties 运行参数
     * @param handlers 任务处理器
     */
    public JobService(
            JobRepository jobs,
            DocumentService documents,
            DocumentVersionRepository versions,
            TextUnitRepository textUnits,
            OntoTraceProperties properties,
            List<JobHandler> handlers) {
        this.jobs = jobs;
        this.documents = documents;
        this.versions = versions;
        this.textUnits = textUnits;
        this.properties = properties;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    /**
     * 提交内容提取任务。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param idempotencyKey 幂等键，可空
     * @return 任务
     */
    @Transactional
    public Job submitExtract(CurrentUser user, UUID documentId, String idempotencyKey) {
        documents.requireEdit(user, documentId);
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? EXTRACT_CONTENT + ":" + documentId
                : idempotencyKey;
        return jobs.findByIdempotencyKey(key)
                .orElseGet(() -> create(EXTRACT_CONTENT, user, documentId, null, null, key));
    }

    /**
     * 提交 EDU 抽取任务。可覆盖全文或单个文本单元。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @param idempotencyKey 幂等键，可空
     * @param textUnitId 局部重抽的文本单元，空表示全文
     * @return 任务
     */
    @Transactional
    public Job submitEdu(CurrentUser user, UUID versionId, String idempotencyKey, UUID textUnitId) {
        DocumentVersion version = versions.findById(versionId).orElseThrow(() -> new NotFoundException("文档版本不存在"));
        documents.requireEdit(user, version.getDocumentId());
        if (textUnitId != null) {
            TextUnit unit = textUnits.findById(textUnitId).orElseThrow(() -> new NotFoundException("文本单元不存在"));
            if (!versionId.equals(unit.getDocumentVersionId())) {
                throw new UnprocessableException("文本单元不属于该版本");
            }
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? EXTRACT_EDU + ":" + versionId + ":" + properties.getAi().getGeneratePromptVersion() + ":"
                        + (textUnitId == null ? "all" : textUnitId) + ":" + UUID.randomUUID()
                : idempotencyKey;
        return jobs.findByIdempotencyKey(key)
                .orElseGet(() -> create(EXTRACT_EDU, user, version.getDocumentId(), versionId, textUnitId, key));
    }

    /**
     * 读取任务，校验文档权限。
     *
     * @param user 当前用户
     * @param jobId 任务标识
     * @return 任务
     */
    public Job get(CurrentUser user, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("任务不存在"));
        if (job.getDocumentId() != null) {
            documents.requireView(user, job.getDocumentId());
        } else if (!job.getCreatedBy().equals(user.id()) && !user.admin()) {
            throw new AccessDeniedException("无权查看该任务");
        }
        return job;
    }

    /**
     * 领取一条到期任务。必须在执行线程之外的短事务中调用。
     *
     * @param workerId 工作进程标识
     * @return 领到的任务，队列为空时返回 {@code null}
     */
    @Transactional
    public Job claim(String workerId) {
        Instant now = Instant.now();
        Job job = jobs.lockNext(now, workerId).orElse(null);
        if (job == null) {
            return null;
        }
        job.setNew(false);
        job.setStatus("running");
        job.setAttemptCount(job.getAttemptCount() == null ? 1 : job.getAttemptCount() + 1);
        job.setLeaseUntil(now.plusSeconds(properties.getWorker().getLeaseSeconds()));
        job.setWorkerId(workerId);
        job.setUpdatedAt(now);
        jobs.save(job);
        log.info(
                "claimed job jobId={} type={} workerId={} attempt={}",
                job.getId(),
                job.getType(),
                workerId,
                job.getAttemptCount());
        return job;
    }

    /**
     * 执行已领取的任务。必须在领取事务外调用。
     *
     * @param claimed 已领取的任务
     */
    public void execute(Job claimed) {
        JobHandler handler = handlers.get(claimed.getType());
        if (handler == null) {
            fail(claimed, "未知任务类型: " + claimed.getType());
            return;
        }
        try {
            handler.execute(claimed);
            succeed(claimed);
        } catch (Exception ex) {
            log.warn("job failed jobId={} type={}", claimed.getId(), claimed.getType(), ex);
            fail(claimed, errorSummary(ex));
        }
    }

    /**
     * 为本进程在途任务续租，避免长任务被其它实例抢走。
     *
     * @param workerId 工作进程标识
     * @param jobIds 在途任务
     */
    @Transactional
    public void renewLeases(String workerId, Collection<UUID> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        Instant leaseUntil = now.plusSeconds(properties.getWorker().getLeaseSeconds());
        int updated = jobs.renewLeases(workerId, jobIds, leaseUntil, now);
        if (updated > 0) {
            log.debug("renewed leases workerId={} count={}", workerId, updated);
        }
    }

    private Job create(
            String type, CurrentUser user, UUID documentId, UUID versionId, UUID textUnitId, String key) {
        Instant now = Instant.now();
        Job job = Job.builder()
                .id(UUID.randomUUID())
                .type(type)
                .status("pending")
                .stage("queued")
                .progress(0)
                .total(null)
                .attemptCount(0)
                .nextRunAt(now)
                .idempotencyKey(key)
                .documentId(documentId)
                .documentVersionId(versionId)
                .targetTextUnitId(textUnitId)
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobs.save(job);
        log.info("created job jobId={} type={}", job.getId(), type);
        return job;
    }

    private void succeed(Job job) {
        job.setStatus("succeeded");
        job.setStage("done");
        job.setUpdatedAt(Instant.now());
        jobs.save(job);
    }

    private void fail(Job job, String summary) {
        job.setStatus("failed");
        job.setErrorSummary(summary);
        job.setUpdatedAt(Instant.now());
        jobs.save(job);
    }

    /**
     * 把任务失败写成可读摘要。客户端超时的根因是 InterruptedIOException，外层往往只剩 Request failed。
     *
     * @param ex 任务执行异常
     * @return 写入 job.error_summary 的文本
     */
    static String errorSummary(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InterruptedIOException) {
                return "模型请求超时。请增大 AI_CHAT_TIMEOUT（spring.ai.openai.timeout）";
            }
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
