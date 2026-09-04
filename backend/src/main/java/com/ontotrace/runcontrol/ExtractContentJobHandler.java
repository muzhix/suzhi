package com.ontotrace.runcontrol;

import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.asset.Asset;
import com.ontotrace.document.asset.AssetRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.document.asset.UploadService;
import com.ontotrace.document.asset.UploadSession;
import com.ontotrace.document.parser.DocumentParser;
import com.ontotrace.document.parser.TextDocumentParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TXT/Markdown 显式提取，生成不可变版本和文本单元。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class ExtractContentJobHandler implements JobHandler {

    private final UploadService uploads;
    private final AssetRepository assets;
    private final S3AssetStore store;
    private final TextDocumentParser parser;
    private final DocumentVersionRepository versions;
    private final TextUnitRepository textUnits;
    private final JobRepository jobs;

    /**
     * 创建处理器。
     *
     * @param uploads 上传服务
     * @param assets 资产仓储
     * @param store 对象存储
     * @param parser 文本解析器
     * @param versions 版本文仓
     * @param textUnits 文本单元仓储
     * @param jobs 任务仓储
     */
    public ExtractContentJobHandler(
            UploadService uploads,
            AssetRepository assets,
            S3AssetStore store,
            TextDocumentParser parser,
            DocumentVersionRepository versions,
            TextUnitRepository textUnits,
            JobRepository jobs) {
        this.uploads = uploads;
        this.assets = assets;
        this.store = store;
        this.parser = parser;
        this.versions = versions;
        this.textUnits = textUnits;
        this.jobs = jobs;
    }

    /**
     * 返回任务类型。
     *
     * @return extract_content
     */
    @Override
    public String type() {
        return JobService.EXTRACT_CONTENT;
    }

    /**
     * 下载资产、解析并写入固定版本。
     *
     * @param job 任务
     */
    @Override
    @Transactional
    public void execute(Job job) {
        if (versions.findFirstByDocumentIdOrderByVersionNoDesc(job.getDocumentId()).isPresent()
                && "succeeded".equals(job.getStatus())) {
            return;
        }
        UploadSession session = uploads.requireCompleted(job.getDocumentId());
        Asset asset = assets.findById(session.getAssetId()).orElseThrow();
        byte[] bytes = store.getObject(asset.getObjectKey());
        DocumentParser.ParseResult parsed = parser.parse(new DocumentParser.ParseRequest(
                asset.getObjectKey(), asset.getOriginalFilename(), asset.getContentType(), bytes));
        if (!"succeeded".equals(parsed.status())) {
            throw new IllegalStateException(parsed.error());
        }
        int nextNo = versions.findByDocumentIdOrderByVersionNoDesc(job.getDocumentId()).stream()
                .mapToInt(DocumentVersion::getVersionNo)
                .max()
                .orElse(0)
                + 1;
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        versions.save(DocumentVersion.builder()
                .id(versionId)
                .documentId(job.getDocumentId())
                .assetId(asset.getId())
                .versionNo(nextNo)
                .contentFingerprint(asset.getChecksumSha256())
                .parserId("text-v1")
                .createdAt(now)
                .build());
        List<String> units = splitUnits(parsed.text());
        int seq = 1;
        List<TextUnit> rows = new ArrayList<>();
        for (String unit : units) {
            rows.add(TextUnit.builder()
                    .id(UUID.randomUUID())
                    .documentVersionId(versionId)
                    .seq(seq++)
                    .path("p" + (seq - 1))
                    .displayText(unit)
                    .pageNo(null)
                    .build());
        }
        textUnits.saveAll(rows);
        job.setDocumentVersionId(versionId);
        job.setProgress(rows.size());
        job.setTotal(rows.size());
        job.setStage("parsed");
        job.setUpdatedAt(now);
        jobs.save(job);
        log.info("extracted content jobId={} versionId={} units={}", job.getId(), versionId, rows.size());
    }

    private static List<String> splitUnits(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> units = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                units.add(trimmed);
            }
        }
        if (units.isEmpty()) {
            units.add(text.trim());
        }
        return units;
    }
}
