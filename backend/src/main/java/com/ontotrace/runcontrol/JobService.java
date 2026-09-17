package com.ontotrace.runcontrol;

import com.ontotrace.config.OntoTraceProperties;
import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.parser.structure.ExtractJobPayload;
import com.ontotrace.document.parser.structure.StructureJson;
import com.ontotrace.document.parser.structure.StructurePaths;
import com.ontotrace.document.parser.structure.StructureProfile;
import com.ontotrace.document.parser.structure.StructureSchemeRequest;
import com.ontotrace.document.parser.structure.DocumentStructureService;
import com.ontotrace.web.ApiExceptionHandler.ConflictException;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** 未终态集合：提交去重时认为仍在执行。 */
    private static final List<String> ACTIVE_STATUSES = List.of("pending", "running");

    private final JobRepository jobs;
    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final TextUnitRepository textUnits;
    private final DocumentStructureService structure;
    private final OntoTraceProperties properties;
    private final Map<String, JobHandler> handlers;

    /**
     * 创建服务。
     *
     * @param jobs 任务仓储
     * @param documents 文档服务
     * @param versions 版本文仓
     * @param textUnits 文本单元仓储
     * @param structure 结构方案
     * @param properties 运行参数
     * @param handlers 任务处理器
     */
    public JobService(
            JobRepository jobs,
            DocumentService documents,
            DocumentVersionRepository versions,
            TextUnitRepository textUnits,
            DocumentStructureService structure,
            OntoTraceProperties properties,
            List<JobHandler> handlers) {
        this.jobs = jobs;
        this.documents = documents;
        this.versions = versions;
        this.textUnits = textUnits;
        this.structure = structure;
        this.properties = properties;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
    }

    /**
     * 提交内容提取任务。同文档存在未完成的提取任务时复用，避免重复提交；
     * 已终态时允许重跑，生成新的不可变版本。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param idempotencyKey 客户端幂等键，可空
     * @param request 可选结构方案；空则空行切段
     * @return 任务
     */
    @Transactional
    public Job submitExtract(CurrentUser user, UUID documentId, String idempotencyKey, StructureSchemeRequest request) {
        documents.requireEdit(user, documentId);
        StructureProfile profile = structure.resolve(request);
        String fingerprint = profile == null ? "none" : profile.id() + ":" + Math.abs(StructureJson.write(profile).hashCode());
        if (hasClientKey(idempotencyKey)) {
            return reuseClientKey(idempotencyKey, user, documentId)
                    .orElseGet(() -> createExtract(user, documentId, idempotencyKey, profile));
        }
        String base = EXTRACT_CONTENT + ":" + documentId + ":" + fingerprint + ":";
        return jobs.findFirstByIdempotencyKeyStartingWithAndStatusInOrderByCreatedAtDesc(base, ACTIVE_STATUSES)
                .orElseGet(() -> createExtract(user, documentId, nextSequenceKey(base), profile));
    }

    /**
     * 提交 EDU 抽取任务。可覆盖全文或单个文本单元；同一版本、范围和提示版本存在
     * 未完成任务时复用，终态后重跑按序号新建。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @param idempotencyKey 客户端幂等键，可空
     * @param textUnitId 局部重抽的文本单元
     * @param pathPrefix 按 path 前缀抽取
     * @param confirmFullDocument 确认全书抽取
     * @return 任务
     */
    @Transactional
    public Job submitEdu(
            CurrentUser user,
            UUID versionId,
            String idempotencyKey,
            UUID textUnitId,
            String pathPrefix,
            boolean confirmFullDocument) {
        if ("disabled".equals(properties.getAi().getMode())) {
            throw new UnprocessableException("未配置模型。设置 AI_API_KEY 并将 ontotrace.ai.mode 设为 live，或本地使用 stub");
        }
        DocumentVersion version = versions.findById(versionId).orElseThrow(() -> new NotFoundException("文档版本不存在"));
        documents.requireEdit(user, version.getDocumentId());
        String prefix = pathPrefix == null || pathPrefix.isBlank() ? null : pathPrefix;
        if (textUnitId != null && prefix != null) {
            throw new UnprocessableException("不能同时指定文本单元和 path 前缀");
        }
        if (textUnitId != null) {
            TextUnit unit = textUnits.findById(textUnitId).orElseThrow(() -> new NotFoundException("文本单元不存在"));
            if (!versionId.equals(unit.getDocumentVersionId())) {
                throw new UnprocessableException("文本单元不属于该版本");
            }
        }
        if (prefix != null) {
            List<TextUnit> scoped = textUnits.findByDocumentVersionIdAndPathPrefix(
                    versionId, prefix, StructurePaths.likeLiteral(prefix) + "/%");
            if (scoped.isEmpty()) {
                throw new UnprocessableException("该 path 下没有文本单元");
            }
        }
        if (textUnitId == null && prefix == null && !confirmFullDocument) {
            throw new UnprocessableException("全书抽取需要选到卷或更细，或勾选全文并查看预算");
        }
        String range = textUnitId != null ? textUnitId.toString() : prefix != null ? "path:" + prefix : "all";
        if (hasClientKey(idempotencyKey)) {
            return reuseClientKey(idempotencyKey, user, version.getDocumentId())
                    .orElseGet(() -> createEdu(user, version.getDocumentId(), versionId, textUnitId, prefix, idempotencyKey));
        }
        String base = EXTRACT_EDU + ":" + versionId + ":" + properties.getAi().getGeneratePromptVersion() + ":"
                + range + ":";
        return jobs.findFirstByIdempotencyKeyStartingWithAndStatusInOrderByCreatedAtDesc(base, ACTIVE_STATUSES)
                .orElseGet(() -> createEdu(
                        user, version.getDocumentId(), versionId, textUnitId, prefix, nextSequenceKey(base)));
    }

    /**
     * 提交 EDU 抽取。未指定范围时视为确认全文，供既有集成测试复用。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @param idempotencyKey 客户端幂等键，可空
     * @param textUnitId 局部重抽的文本单元，空表示全文
     * @return 任务
     */
    @Transactional
    public Job submitEdu(CurrentUser user, UUID versionId, String idempotencyKey, UUID textUnitId) {
        return submitEdu(user, versionId, idempotencyKey, textUnitId, null, textUnitId == null);
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
     * 执行已领取的任务。必须在领取事务外调用。可恢复错误按退避重排，
     * 超过重试上限或永久错误写失败摘要。
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
            if (isRetryable(ex)) {
                retry(claimed, ex);
            } else {
                log.warn("job failed permanently jobId={} type={}", claimed.getId(), claimed.getType(), ex);
                fail(claimed, errorSummary(ex));
            }
        }
    }

    /**
     * 判断异常是否可恢复。输入与状态类错误（4xx 语义、参数非法）重试无意义；
     * 网络超时、对象存储瞬断、5xx 与模型输出解析失败属于可恢复。
     *
     * @param ex 任务异常
     * @return 可恢复返回 true
     */
    static boolean isRetryable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof UnprocessableException
                    || t instanceof ConflictException
                    || t instanceof NotFoundException
                    || t instanceof IllegalStateException) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按指数退避重排任务：30s 起步、翻倍、上限 10 分钟；超过重试上限转终态失败。
     *
     * @param job 任务
     * @param ex 异常
     */
    private void retry(Job job, Exception ex) {
        int attempt = job.getAttemptCount() == null ? 1 : job.getAttemptCount();
        if (attempt >= properties.getWorker().getMaxAttempts()) {
            log.warn("job exceeded retry limit jobId={} attempt={}", job.getId(), attempt, ex);
            fail(job, errorSummary(ex));
            return;
        }
        long backoffSeconds = Math.min(30L << (attempt - 1), 600L);
        job.setStatus("pending");
        job.setStage("queued");
        job.setNextRunAt(Instant.now().plusSeconds(backoffSeconds));
        job.setLeaseUntil(null);
        job.setWorkerId(null);
        job.setErrorSummary(errorSummary(ex));
        job.setUpdatedAt(Instant.now());
        jobs.save(job);
        log.info(
                "job rescheduled jobId={} attempt={} backoffSeconds={} error={}",
                job.getId(),
                attempt,
                backoffSeconds,
                errorSummary(ex));
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

    private static boolean hasClientKey(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    /**
     * 客户端幂等键命中已存在任务时复用；键被其他用户或其他文档占用时拒绝，避免信息泄漏。
     *
     * @param key 客户端幂等键
     * @param user 当前用户
     * @param documentId 任务所属文档
     * @return 命中的任务
     */
    private Optional<Job> reuseClientKey(String key, CurrentUser user, UUID documentId) {
        return jobs.findByIdempotencyKey(key).map(job -> {
            if (!job.getCreatedBy().equals(user.id()) || !documentId.equals(job.getDocumentId())) {
                throw new ConflictException("幂等键已被其他任务使用");
            }
            return job;
        });
    }

    /**
     * 生成默认序号键：前缀 + 历史任务数 + 1。序号保证 UNIQUE 约束下的重跑可写。
     *
     * @param base 参数前缀，以冒号结尾
     * @return 新幂等键
     */
    private String nextSequenceKey(String base) {
        return base + (jobs.countByIdempotencyKeyStartingWith(base) + 1);
    }

    private Job createExtract(CurrentUser user, UUID documentId, String key, StructureProfile profile) {
        Job job = create(EXTRACT_CONTENT, user, documentId, null, null, key);
        if (profile != null) {
            jobs.setPayload(job.getId(), StructureJson.write(new ExtractJobPayload(profile.id(), profile)));
        }
        return job;
    }

    private Job createEdu(
            CurrentUser user, UUID documentId, UUID versionId, UUID textUnitId, String pathPrefix, String key) {
        Job job = create(EXTRACT_EDU, user, documentId, versionId, textUnitId, key);
        if (pathPrefix != null) {
            jobs.setPayload(job.getId(), StructureJson.write(new EduPathPayload(pathPrefix)));
        }
        return job;
    }

    /**
     * EDU 任务的 path 前缀负载。
     *
     * @param pathPrefix 结构路径前缀
     */
    public record EduPathPayload(String pathPrefix) {}

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
