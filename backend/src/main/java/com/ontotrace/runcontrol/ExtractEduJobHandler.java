package com.ontotrace.runcontrol;

import com.ontotrace.config.OntoTraceProperties;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.document.parser.structure.StructureJson;
import com.ontotrace.document.parser.structure.StructurePaths;
import com.ontotrace.runcontrol.JobService.EduPathPayload;
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
 * 复核步骤受 ontotrace.ai.review-enabled 控制，默认关闭：跳过时不调用复核模型，
 * EDU 状态仅由确定性条件（无外部知识且定位精确）决定，复核字段写空。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class ExtractEduJobHandler implements JobHandler {

    private final TextUnitRepository textUnitRepo;
    private final ContextAssembler assembler;
    private final EduModelGateway eduModelGateway;
    private final EduBatchWriter writer;
    private final JobRepository jobRepo;
    private final ProcessingRunRepository processingRunRepo;
    private final S3AssetStore store;
    private final OntoTraceProperties properties;
    private final SourceLocator locator = new SourceLocator();
    private final EduValidator validator = new EduValidator();

    /**
     * 创建处理器。
     *
     * @param textUnitRepo 文本单元仓储
     * @param assembler 上下文组装
     * @param eduModelGateway 模型网关
     * @param writer EDU 批量写入
     * @param jobRepo 任务仓储
     * @param processingRunRepo 处理运行仓储
     * @param store 对象存储，保存丢弃输出的运行附件
     * @param properties 运行参数
     */
    public ExtractEduJobHandler(
            TextUnitRepository textUnitRepo,
            ContextAssembler assembler,
            EduModelGateway eduModelGateway,
            EduBatchWriter writer,
            JobRepository jobRepo,
            ProcessingRunRepository processingRunRepo,
            S3AssetStore store,
            OntoTraceProperties properties) {
        this.textUnitRepo = textUnitRepo;
        this.assembler = assembler;
        this.eduModelGateway = eduModelGateway;
        this.writer = writer;
        this.jobRepo = jobRepo;
        this.processingRunRepo = processingRunRepo;
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
        List<TextUnit> units = textUnitRepo.findByDocumentVersionIdOrderBySeqAsc(versionId);
        UUID targetId = job.getTargetTextUnitId();
        String pathPrefix = loadPathPrefix(job);
        List<UUID> prefixUnitIds = pathPrefix == null
                ? null
                : units.stream()
                        .filter(unit -> StructurePaths.underPrefix(unit.getPath(), pathPrefix))
                        .map(TextUnit::getId)
                        .toList();
        writer.deleteExisting(versionId, targetId, prefixUnitIds);
        ProcessingRun run = startRun(job, versionId, targetId, pathPrefix);
        List<Integer> indexes = targetIndexes(units, targetId, pathPrefix);
        job.setNew(false);
        job.setTotal(indexes.size());
        job.setStage("generating");
        jobRepo.save(job);
        RunStats stats = new RunStats();
        try {
            int done = 0;
            for (int i : indexes) {
                ContextAssembler.Assembled assembled = assembler.assemble(units, i);
                EduModelGateway.EduGenerationResult generated = eduModelGateway.generate(
                        new EduModelGateway.EduGenerationRequest(assembled.prompt(), assembled.targetText()));
                stats.addGeneration(generated);
                List<EduValidator.ModelEdu> dropped = new ArrayList<>();
                for (EduValidator.ModelEdu modelEdu : generated.edus()) {
                    EduModelGateway.EduReviewResult review = persistOne(run.getId(), versionId, assembled, modelEdu);
                    if (review == null) {
                        dropped.add(modelEdu);
                    } else if (review != EduModelGateway.SKIPPED) {
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
                jobRepo.save(job);
            }
            finishRun(run, stats, true, null);
        } catch (Exception ex) {
            finishRun(run, stats, false, ex.getMessage());
            throw ex;
        }
        log.info(
                "extracted edu jobId={} versionId={} targetTextUnitId={} pathPrefix={} units={} dropped={} reviewEnabled={} tokenIn={} tokenOut={}",
                job.getId(),
                versionId,
                targetId,
                pathPrefix,
                indexes.size(),
                stats.dropped,
                properties.getAi().isReviewEnabled(),
                stats.tokenInput,
                stats.tokenOutput);
    }

    private ProcessingRun startRun(Job job, UUID versionId, UUID targetId, String pathPrefix) {
        OntoTraceProperties.Ai ai = properties.getAi();
        boolean reviewEnabled = ai.isReviewEnabled();
        String inputRange = targetId != null ? targetId.toString() : pathPrefix != null ? "path:" + pathPrefix : "all";
        ProcessingRun run = ProcessingRun.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .inputDocumentVersionId(versionId)
                .inputRange(inputRange)
                .contextStrategy("neighbor-1+target")
                .provider(ai.getProvider())
                .modelId(ai.getChatModel())
                .promptVersion(ai.getGeneratePromptVersion())
                .outputSchemaVersion(ai.getOutputSchemaVersion())
                .reviewModelId(reviewEnabled ? ai.getReviewModel() : null)
                .reviewPromptVersion(reviewEnabled ? ai.getReviewPromptVersion() : null)
                .status("running")
                .createdAt(Instant.now())
                .build();
        processingRunRepo.save(run);
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
        processingRunRepo.save(run);
        processingRunRepo.setReviewSummary(run.getId(), EduJsonMapper.write(stats.reviewSummary(error)));
        processingRunRepo.setParameters(
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

    private String loadPathPrefix(Job job) {
        String json = jobRepo.findPayload(job.getId()).orElse(null);
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        EduPathPayload payload = StructureJson.read(json, EduPathPayload.class);
        if (payload == null || payload.pathPrefix() == null || payload.pathPrefix().isBlank()) {
            return null;
        }
        return payload.pathPrefix();
    }

    private static List<Integer> targetIndexes(List<TextUnit> units, UUID textUnitId, String pathPrefix) {
        if (textUnitId != null) {
            for (int i = 0; i < units.size(); i++) {
                if (textUnitId.equals(units.get(i).getId())) {
                    return List.of(i);
                }
            }
            throw new IllegalStateException("任务指定的文本单元不在该版本中");
        }
        java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            TextUnit unit = units.get(i);
            if (pathPrefix != null && !StructurePaths.underPrefix(unit.getPath(), pathPrefix)) {
                continue;
            }
            if (StructurePaths.skipEdu(unit.getPath())) {
                continue;
            }
            indexes.add(i);
        }
        return indexes;
    }

    /**
     * 校验、定位并写入一条 EDU。全部来源逐条定位：第一条为主要原文，其余为补全上下文；
     * 模型未返回来源时不补造摘录，直接按确定性校验失败丢弃。
     * 复核关闭时跳过模型调用，EDU 的 active 判定只看确定性条件，复核字段写空。
     *
     * @return 复核结果，复核关闭时为 {@link EduModelGateway#SKIPPED}；未通过确定性校验时返回 {@code null}
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
        boolean reviewEnabled = properties.getAi().isReviewEnabled();
        EduModelGateway.EduReviewResult review = reviewEnabled
                ? eduModelGateway.review(new EduModelGateway.EduReviewRequest(assembled.prompt(), modelEdu))
                : EduModelGateway.SKIPPED;
        boolean autoActive = (!reviewEnabled || review.passed() && review.flags().isEmpty())
                && !modelEdu.usedExternalKnowledge()
                && primary.precision() == SourceLocator.Precision.exact;
        String reviewResult = null;
        String reviewNotes = null;
        if (reviewEnabled) {
            reviewResult = review.passed() ? "passed" : "flagged";
            reviewNotes = review.note();
        } else {
            log.debug("edu review skipped versionId={}", versionId);
        }
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
                .reviewResult(reviewResult)
                .reviewNotes(reviewNotes)
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
