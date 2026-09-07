package com.ontotrace.runcontrol;

import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内容提取产物的短事务写入。S3 下载与解析在事务外完成后，由本类把版本、文本单元
 * 和任务进度原子落库；任务行的版本标识随本事务持久，作为重试时的已完成步骤记录。
 *
 * @author hanbd
 */
@Component
public class ExtractContentWriter {

    private final DocumentVersionRepository versions;
    private final TextUnitRepository textUnits;
    private final JobRepository jobs;

    /**
     * 创建写入器。
     *
     * @param versions 版本文仓
     * @param textUnits 文本单元仓储
     * @param jobs 任务仓储
     */
    public ExtractContentWriter(
            DocumentVersionRepository versions, TextUnitRepository textUnits, JobRepository jobs) {
        this.versions = versions;
        this.textUnits = textUnits;
        this.jobs = jobs;
    }

    /**
     * 写入不可变版本与文本单元，并持久任务进度。
     *
     * @param job 任务
     * @param documentId 文档标识
     * @param assetId 资产标识
     * @param contentFingerprint 内容指纹
     * @param parserId 解析器标识
     * @param unitTexts 顺序文本单元内容
     * @return 版本标识
     */
    @Transactional
    public UUID writeVersion(
            Job job, UUID documentId, UUID assetId, String contentFingerprint, String parserId, List<String> unitTexts) {
        int nextNo = versions.findByDocumentIdOrderByVersionNoDesc(documentId).stream()
                .mapToInt(DocumentVersion::getVersionNo)
                .max()
                .orElse(0)
                + 1;
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        versions.save(DocumentVersion.builder()
                .id(versionId)
                .documentId(documentId)
                .assetId(assetId)
                .versionNo(nextNo)
                .contentFingerprint(contentFingerprint)
                .parserId(parserId)
                .createdAt(now)
                .build());
        List<TextUnit> rows = new ArrayList<>();
        int seq = 1;
        for (String unit : unitTexts) {
            rows.add(TextUnit.builder()
                    .id(UUID.randomUUID())
                    .documentVersionId(versionId)
                    .seq(seq)
                    .path("p" + seq)
                    .displayText(unit)
                    .pageNo(null)
                    .build());
            seq++;
        }
        textUnits.saveAll(rows);
        job.setDocumentVersionId(versionId);
        job.setProgress(rows.size());
        job.setTotal(rows.size());
        job.setStage("parsed");
        job.setUpdatedAt(now);
        jobs.save(job);
        return versionId;
    }
}
