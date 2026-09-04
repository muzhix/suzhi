package com.ontotrace.runcontrol;

import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.semantic.Edu;
import com.ontotrace.semantic.EduArgument;
import com.ontotrace.semantic.EduSourceRef;
import com.ontotrace.semantic.context.ContextAssembler;
import com.ontotrace.semantic.extraction.EduModelGateway;
import com.ontotrace.semantic.extraction.EduValidator;
import com.ontotrace.semantic.extraction.SourceLocator;
import com.ontotrace.semantic.persistence.EduArgumentRepository;
import com.ontotrace.semantic.persistence.EduRepository;
import com.ontotrace.semantic.persistence.EduSourceRefRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 显式 EDU 抽取：生成、定位、校验、复核、幂等写入。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class ExtractEduJobHandler implements JobHandler {

    private final TextUnitRepository textUnits;
    private final ContextAssembler assembler;
    private final EduModelGateway models;
    private final EduRepository edus;
    private final EduSourceRefRepository sources;
    private final EduArgumentRepository arguments;
    private final JobRepository jobs;
    private final SourceLocator locator = new SourceLocator();
    private final EduValidator validator = new EduValidator();

    /**
     * 创建处理器。
     *
     * @param textUnits 文本单元仓储
     * @param assembler 上下文组装
     * @param models 模型网关
     * @param edus EDU 仓储
     * @param sources 来源仓储
     * @param arguments 参数仓储
     * @param jobs 任务仓储
     */
    public ExtractEduJobHandler(
            TextUnitRepository textUnits,
            ContextAssembler assembler,
            EduModelGateway models,
            EduRepository edus,
            EduSourceRefRepository sources,
            EduArgumentRepository arguments,
            JobRepository jobs) {
        this.textUnits = textUnits;
        this.assembler = assembler;
        this.models = models;
        this.edus = edus;
        this.sources = sources;
        this.arguments = arguments;
        this.jobs = jobs;
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
     * 对范围内的文本单元抽取 EDU。先删除对应 EDU 再写入。
     *
     * @param job 任务
     */
    @Override
    @Transactional
    public void execute(Job job) {
        UUID versionId = job.getDocumentVersionId();
        List<TextUnit> units = textUnits.findByDocumentVersionIdOrderBySeqAsc(versionId);
        UUID targetId = job.getTargetTextUnitId();
        replaceExisting(versionId, targetId);
        List<Integer> indexes = targetIndexes(units, targetId);
        job.setNew(false);
        job.setTotal(indexes.size());
        job.setStage("generating");
        jobs.save(job);
        int done = 0;
        for (int i : indexes) {
            ContextAssembler.Assembled assembled = assembler.assemble(units, i);
            EduModelGateway.EduGenerationResult generated = models.generate(
                    new EduModelGateway.EduGenerationRequest(assembled.prompt(), assembled.targetText()));
            for (EduValidator.ModelEdu modelEdu : generated.edus()) {
                persistOne(versionId, assembled, modelEdu);
            }
            done++;
            job.setProgress(done);
            jobs.save(job);
        }
        log.info(
                "extracted edu jobId={} versionId={} targetTextUnitId={} units={}",
                job.getId(),
                versionId,
                targetId,
                indexes.size());
    }

    private void replaceExisting(UUID versionId, UUID textUnitId) {
        if (textUnitId == null) {
            arguments.deleteByDocumentVersionId(versionId);
            sources.deleteByDocumentVersionId(versionId);
            edus.deleteByDocumentVersionId(versionId);
            log.info("replaced all edu versionId={}", versionId);
            return;
        }
        List<UUID> eduIds = sources.findEduIdsByTextUnitId(textUnitId);
        if (eduIds.isEmpty()) {
            return;
        }
        arguments.deleteByEduIdIn(eduIds);
        sources.deleteByEduIdIn(eduIds);
        edus.deleteByIdIn(eduIds);
        log.info("replaced edu versionId={} textUnitId={} count={}", versionId, textUnitId, eduIds.size());
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

    private void persistOne(UUID versionId, ContextAssembler.Assembled assembled, EduValidator.ModelEdu modelEdu) {
        EduValidator.Source primary = modelEdu.sources() == null || modelEdu.sources().isEmpty()
                ? new EduValidator.Source("target", assembled.targetText())
                : modelEdu.sources().getFirst();
        SourceLocator.Location location = locator.locate(assembled, primary.contextKey(), primary.quote());
        boolean failed = location.precision() == SourceLocator.Precision.failed;
        List<String> errors = validator.validate(modelEdu, failed);
        if (!errors.isEmpty()) {
            log.info("edu dropped versionId={} errors={}", versionId, errors);
            return;
        }
        EduModelGateway.EduReviewResult review =
                models.review(new EduModelGateway.EduReviewRequest(assembled.prompt(), modelEdu));
        boolean autoActive = review.passed()
                && review.flags().isEmpty()
                && !modelEdu.usedExternalKnowledge()
                && location.precision() == SourceLocator.Precision.exact;
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
                .locationPrecision(location.precision().name())
                .usedExternalKnowledge(modelEdu.usedExternalKnowledge())
                .reviewResult(review.passed() ? "passed" : "flagged")
                .reviewNotes(review.note())
                .createdAt(now)
                .updatedAt(now)
                .build();
        edus.save(edu);
        sources.save(EduSourceRef.builder()
                .id(UUID.randomUUID())
                .eduId(edu.getId())
                .documentVersionId(versionId)
                .textUnitId(location.textUnitId())
                .quote(location.quote())
                .charStart(location.charStart())
                .charEnd(location.charEnd())
                .precision(location.precision() == SourceLocator.Precision.exact ? "exact" : "unit")
                .purpose("primary")
                .build());
        if (modelEdu.arguments() != null) {
            for (EduValidator.Argument argument : modelEdu.arguments()) {
                arguments.save(EduArgument.builder()
                        .id(UUID.randomUUID())
                        .eduId(edu.getId())
                        .role(argument.role())
                        .roleName(argument.roleName())
                        .value(argument.value())
                        .sourceForm(argument.sourceForm())
                        .entityType(argument.entityType())
                        .build());
            }
        }
    }
}
