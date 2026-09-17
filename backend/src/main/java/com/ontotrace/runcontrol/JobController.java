package com.ontotrace.runcontrol;

import com.ontotrace.security.CurrentUser;
import com.ontotrace.document.parser.structure.StructureSchemeRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务进度接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;

    /**
     * 创建控制器。
     *
     * @param jobService 任务服务
     */
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 提交内容提取。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param idempotencyKey 幂等键
     * @param request 可选结构方案
     * @return 202 与任务
     */
    @PostMapping("/documents/{documentId}/extraction-jobs")
    public ResponseEntity<JobResponse> extract(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) StructureSchemeRequest request) {
        Job job = jobService.submitExtract(user, documentId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(job));
    }

    /**
     * 提交 EDU 抽取。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @param idempotencyKey 幂等键
     * @param request 可选局部重抽
     * @return 202 与任务
     */
    @PostMapping("/document-versions/{versionId}/edu-jobs")
    public ResponseEntity<JobResponse> extractEdu(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID versionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ExtractEduRequest request) {
        UUID textUnitId = request == null ? null : request.textUnitId();
        String pathPrefix = request == null ? null : request.pathPrefix();
        boolean confirmFull = request != null && Boolean.TRUE.equals(request.confirmFullDocument());
        Job job = jobService.submitEdu(user, versionId, idempotencyKey, textUnitId, pathPrefix, confirmFull);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(job));
    }

    /**
     * 读取任务进度。
     *
     * @param user 当前用户
     * @param jobId 任务标识
     * @return 任务
     */
    @GetMapping("/jobs/{jobId}")
    public JobResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID jobId) {
        return JobResponse.from(jobService.get(user, jobId));
    }

    /**
     * EDU 抽取请求。
     *
     * @param textUnitId 局部重抽的文本单元
     * @param pathPrefix 按 path 前缀抽取本卷/本篇/本年
     * @param confirmFullDocument 确认全书抽取并已知预算
     */
    public record ExtractEduRequest(UUID textUnitId, String pathPrefix, Boolean confirmFullDocument) {}

    /**
     * 任务响应。
     *
     * @param jobId 任务标识
     * @param type 类型
     * @param status 状态
     * @param stage 阶段
     * @param progress 进度
     * @param total 总量
     * @param documentVersionId 版本标识
     * @param jobUrl 任务资源地址
     * @param errorSummary 失败摘要
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public record JobResponse(
            String jobId,
            String type,
            String status,
            String stage,
            Integer progress,
            Integer total,
            String documentVersionId,
            String jobUrl,
            String errorSummary,
            Instant createdAt,
            Instant updatedAt) {
        static JobResponse from(Job job) {
            return new JobResponse(
                    job.getId().toString(),
                    job.getType(),
                    job.getStatus(),
                    job.getStage(),
                    job.getProgress(),
                    job.getTotal(),
                    job.getDocumentVersionId() == null ? null : job.getDocumentVersionId().toString(),
                    "/api/jobs/" + job.getId(),
                    job.getErrorSummary(),
                    job.getCreatedAt(),
                    job.getUpdatedAt());
        }
    }
}
