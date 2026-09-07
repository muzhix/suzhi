package com.ontotrace.runcontrol;

import com.ontotrace.config.OntoTraceProperties;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.semantic.Edu;
import com.ontotrace.semantic.EduArgument;
import com.ontotrace.semantic.EduSourceRef;
import com.ontotrace.semantic.context.ContextAssembler;
import com.ontotrace.semantic.extraction.EduJsonMapper;
import com.ontotrace.semantic.extraction.EduModelGateway;
import com.ontotrace.semantic.extraction.EduValidator;
import com.ontotrace.semantic.extraction.SourceLocator;
import com.ontotrace.semantic.persistence.EduBatchWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 显式 EDU 抽取：删除范围内旧结果后生成、定位、校验、复核，逐条短事务写入。
 * 模型调用发生在数据库事务外，进度即时提交，崩溃时已写入条目保留；
 * 模型、提示版本、词元、延迟与丢弃输出记录到处理运行。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class ExtractEduJobHandler implements JobHandler {

    private final TextUnitRepository textUnits;
    private final ContextAssembler assembler;
    private final EduModelGateway models;
    private final EduBatchWriter writer;
    private final JobRepository jobs;
    private final ProcessingRunRepository runs;
    private final S3AssetStore store;
    private final OntoTraceProperties properties;
    private final SourceLocator locator = new SourceLocator();
    private final EduValidator validator = new EduValidator();

    /**
     * 创建处理器。
     *
     * @param textUnits 文本单元仓储
     * @param assembler 上下文组装
     * @param models 模型网关
     * @param writer EDU 批量写入
     * @param jobs 任务仓储
     * @param runs 处理运行仓储
     * @param store 对象存储，保存丢弃输出的运行附件
     * @param properties 运行参数
     */
    public ExtractEduJobHandler(
            TextUnitRepository textUnits,
            ContextAssembler assembler,
            EduModelGateway models,
            EduBatchWriter writer,
            JobRepository jobs,
            ProcessingRunRepository runs,
            S3AssetStore store,
            OntoTraceProperties properties) {
        this.textUnits = textUnits;
        this.assembler = assembler;
        this.models = models;
        this.writer = writer;
        this.jobs = jobs;
        this.runs = runs;
        this.store = store;
        this.properties = properties;
    }

    /**
     * 返回任务类型。
     *
     * @return extract_edu
     */
    @Override
    public String type() {
        return JobService.EXTRACT_EDU;
    }

    /**
     * 对范围内的文本单元抽取 EDU。先删除范围内旧 EDU（含上次尝试的半成品）再重写。
     *
     * @param job 任务
     */
    @Override
    public void execute(Job job) {
        UUID versionId = job.getDocumentVersionId();
        List<TextUnit> units = textUnits.findByDocumentVersionIdOrderBySeqAsc(versionId);
        UUID targetId = job.getTargetTextUnitId();
        writer.deleteExisting(versionId, targetId);
        ProcessingRun run = startRun(job, versionId, targetId);
        List<Integer> indexes = targetIndexes(units, targetId);
        job.setNew(false);
        job.setTotal(indexes.size());
        job.setStage("generating");
        jobs.save(job);
        RunStats stats = new RunStats();
        try {
            int done = 0;
            for (int i : indexes) {
                ContextAssembler.Assembled assembled = assembler.assemble(units, i);
                EduModelGateway.EduGenerationResult generated = models.generate(
                        new EduModelGateway.EduGenerationRequest(assembled.prompt(), assembled.targetText()));
                stats.addGeneration(generated);
                List<EduValidator.ModelEdu> dropped = new ArrayList<>();
                for (EduValidator.ModelEdu modelEdu : generated.edus()) {
                    EduModelGateway.EduReviewResult review = persistOne(run.getId(), versionId, assembled, modelEdu);
                    if (review == null) {
                        dropped.add(modelEdu);
                    } else {
                        stats.addReview(review);
                    }
                }
                if (!dropped.isEmpty()) {
                    UUID targetUnitId = assembled.blocks().stream()
                            .filter(block -> "target".equals(block.key()))
                            .map(ContextAssembler.Block::textUnitId)
                            .findFirst()
                            .orElse(null);
                    writeDroppedAttachment(run, stats, targetUnitId, dropped);
                }
                done++;
                job.setProgress(done);
                jobs.save(job);
            }
            finishRun(run, stats, true, null);
        } catch (Exception ex) {
            finishRun(run, stats, false, ex.getMessage());
            throw ex;
        }
        log.info(
                "extracted edu jobId={} versionId={} targetTextUnitId={} units={} dropped={} tokenIn={} tokenOut={}",
                job.getId(),
                versionId,
                targetId,
                indexes.size(),
                stats.dropped,
                stats.tokenInput,
                stats.tokenOutput);
    }

    private ProcessingRun startRun(Job job, UUID versionId, UUID targetId) {
        OntoTraceProperties.Ai ai = properties.getAi();
        ProcessingRun run = ProcessingRun.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .inputDocumentVersionId(versionId)
                .inputRange(targetId == null ? "all" : targetId.toString())
                .contextStrategy("neighbor-1+target")
                .provider(ai.getProvider())
                .modelId(ai.getChatModel())
                .promptVersion(ai.getGeneratePromptVersion())
                .outputSchemaVersion(ai.getOutputSchemaVersion())
                .reviewModelId(ai.getReviewModel())
                .reviewPromptVersion(ai.getReviewPromptVersion())
                .status("running")
                .createdAt(Instant.now())
                .build();
        runs.save(run);
        return run;
    }

    private void finishRun(ProcessingRun run, RunStats stats, boolean succeeded, String error) {
        run.setNew(false);
        run.setStatus(succeeded ? "succeeded" : "failed");
        if (stats.generationModelId != null) {
            run.setModelId(stats.generationModelId);
            run.setPromptVersion(stats.generationPromptVersion);
        }
        if (stats.reviewModelId != null) {
            run.setReviewModelId(stats.reviewModelId);
        }
        run.setTokenInput(stats.tokenInput);
        run.setTokenOutput(stats.tokenOutput);
        run.setLatencyMs((int) Math.min(stats.latencyMs, Integer.MAX_VALUE));
        run.setAttachmentObjectKey(stats.attachmentKey);
        run.setFinishedAt(Instant.now());
        runs.save(run);
        runs.setReviewSummary(run.getId(), EduJsonMapper.write(stats.reviewSummary(error)));
        runs.setParameters(
                run.getId(),
                EduJsonMapper.write(new RunParameters("runs/" + run.getId() + "/", stats.dropped, error)));
        if (!succeeded) {
            log.warn("edu run failed runId={} error={}", run.getId(), error);
        }
    }

    /**
     * 未通过确定性校验的模型输出只保存为运行附件，不进入 EDU 表。
     */
    private void writeDroppedAttachment(
            ProcessingRun run, RunStats stats, UUID textUnitId, List<EduValidator.ModelEdu> dropped) {
        stats.dropped += dropped.size();
        stats.attachmentSeq++;
        String key = "runs/" + run.getId() + "/dropped-" + stats.attachmentSeq + ".json";
        String payload = EduJsonMapper.write(new DroppedPayload(textUnitId, dropped));
        store.putObject(key, "application/json", payload.getBytes(StandardCharsets.UTF_8));
        if (stats.attachmentKey == null) {
            stats.attachmentKey = key;
        }
        log.info("edu dropped attachment runId={} key={} count={}", run.getId(), key, dropped.size());
    }

    private static List<Integer> targetIndexes(List<TextUnit> units, UUID textUnitId) {
        if (textUnitId == null) {
            java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
            for (int i = 0; i < units.size(); i++) {
                indexes.add(i);
            }
            return indexes;
        }
        for (int i = 0; i < units.size(); i++) {
            if (textUnitId.equals(units.get(i).getId())) {
                return List.of(i);
            }
        }
        throw new IllegalStateException("任务指定的文本单元不在该版本中");
    }

    /**
     * 校验、定位并写入一条 EDU。全部来源逐条定位：第一条为主要原文，其余为补全上下文；
     * 模型未返回来源时不补造摘录，直接按确定性校验失败丢弃。
     *
     * @return 复核结果；未通过确定性校验时返回 {@code null}
     */
    private EduModelGateway.EduReviewResult persistOne(
            UUID runId, UUID versionId, ContextAssembler.Assembled assembled, EduValidator.ModelEdu modelEdu) {
        List<SourceLocator.Location> locations = new ArrayList<>();
        boolean failed = false;
        for (EduValidator.Source source :
                modelEdu.sources() == null ? List.<EduValidator.Source>of() : modelEdu.sources()) {
            SourceLocator.Location location = locator.locate(assembled, source.contextKey(), source.quote());
            if (location.precision() == SourceLocator.Precision.failed) {
                failed = true;
                break;
            }
            locations.add(location);
        }
        List<String> errors = validator.validate(modelEdu, failed);
        if (!errors.isEmpty()) {
            log.info("edu dropped versionId={} errors={}", versionId, errors);
            return null;
        }
        SourceLocator.Location primary = locations.getFirst();
        EduModelGateway.EduReviewResult review =
                models.review(new EduModelGateway.EduReviewRequest(assembled.prompt(), modelEdu));
        boolean autoActive = review.passed()
                && review.flags().isEmpty()
                && !modelEdu.usedExternalKnowledge()
                && primary.precision() == SourceLocator.Precision.exact;
        Instant now = Instant.now();
        Edu edu = Edu.builder()
                .id(UUID.randomUUID())
                .documentVersionId(versionId)
                .type(modelEdu.type())
                .text(modelEdu.text())
                .predicate(modelEdu.predicate())
                .timeValue(modelEdu.time() == null ? null : modelEdu.time().value())
                .timeSourceForm(modelEdu.time() == null ? null : modelEdu.time().sourceForm())
                .timePrecision(modelEdu.time() == null ? null : modelEdu.time().precision())
                .status(autoActive ? "active" : "proposed")
                .revision(1L)
                .locationPrecision(primary.precision().name())
                .usedExternalKnowledge(modelEdu.usedExternalKnowledge())
                .reviewResult(review.passed() ? "passed" : "flagged")
                .reviewNotes(review.note())
                .processingRunId(runId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        List<EduSourceRef> sourceRefs = new ArrayList<>();
        for (int s = 0; s < locations.size(); s++) {
            SourceLocator.Location location = locations.get(s);
            sourceRefs.add(EduSourceRef.builder()
                    .id(UUID.randomUUID())
                    .eduId(edu.getId())
                    .documentVersionId(versionId)
                    .textUnitId(location.textUnitId())
                    .quote(location.quote())
                    .charStart(location.charStart())
                    .charEnd(location.charEnd())
                    .precision(location.precision() == SourceLocator.Precision.exact ? "exact" : "unit")
                    .purpose(s == 0 ? "primary" : "context")
                    .build());
        }
        List<EduArgument> eduArguments = modelEdu.arguments() == null
                ? List.of()
                : modelEdu.arguments().stream()
                        .map(argument -> EduArgument.builder()
                                .id(UUID.randomUUID())
                                .eduId(edu.getId())
                                .role(argument.role())
                                .roleName(argument.roleName())
                                .value(argument.value())
                                .sourceForm(argument.sourceForm())
                                .entityType(argument.entityType())
                                .build())
                        .toList();
        writer.write(edu, sourceRefs, eduArguments);
        return review;
    }

    /**
     * 丢弃输出附件负载。
     *
     * @param textUnitId 来源文本单元
     * @param edus 未通过校验的模型原始输出
     */
    record DroppedPayload(UUID textUnitId, List<EduValidator.ModelEdu> edus) {}

    /**
     * 运行参数 jsonb 负载。
     *
     * @param attachmentPrefix 附件对象键前缀
     * @param droppedCount 丢弃条数
     * @param error 失败摘要
     */
    record RunParameters(String attachmentPrefix, int droppedCount, String error) {}

    /**
     * 一次运行的累计统计。生成与复核的模型、提示版本以网关首次返回值为准。
     */
    private static class RunStats {
        int tokenInput;
        int tokenOutput;
        long latencyMs;
        int reviewPassed;
        int reviewFlagged;
        int dropped;
        int attachmentSeq;
        String attachmentKey;
        String generationModelId;
        String generationPromptVersion;
        String reviewModelId;

        void addGeneration(EduModelGateway.EduGenerationResult result) {
            tokenInput += result.tokenInput();
            tokenOutput += result.tokenOutput();
            latencyMs += result.latencyMs();
            if (generationModelId == null) {
                generationModelId = result.modelId();
                generationPromptVersion = result.promptVersion();
            }
        }

        void addReview(EduModelGateway.EduReviewResult result) {
            tokenInput += result.tokenInput();
            tokenOutput += result.tokenOutput();
            latencyMs += result.latencyMs();
            reviewModelId = result.modelId();
            if (result.passed() && result.flags().isEmpty()) {
                reviewPassed++;
            } else {
                reviewFlagged++;
            }
        }

        ReviewSummary reviewSummary(String error) {
            return new ReviewSummary(reviewPassed, reviewFlagged, reviewModelId, error);
        }

        record ReviewSummary(int passed, int flagged, String reviewModelId, String error) {}
    }
}
