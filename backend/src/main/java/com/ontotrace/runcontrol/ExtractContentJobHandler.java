package com.ontotrace.runcontrol;

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

/**
 * TXT/Markdown 显式提取，生成不可变版本和文本单元。S3 下载与解析在数据库事务外执行，
 * 版本写入由 {@link ExtractContentWriter} 以短事务完成，运行信息记录到处理运行。
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
    private final ExtractContentWriter writer;
    private final ProcessingRunRepository runs;

    /**
     * 创建处理器。
     *
     * @param uploads 上传服务
     * @param assets 资产仓储
     * @param store 对象存储
     * @param parser 文本解析器
     * @param writer 版本写入器
     * @param runs 处理运行仓储
     */
    public ExtractContentJobHandler(
            UploadService uploads,
            AssetRepository assets,
            S3AssetStore store,
            TextDocumentParser parser,
            ExtractContentWriter writer,
            ProcessingRunRepository runs) {
        this.uploads = uploads;
        this.assets = assets;
        this.store = store;
        this.parser = parser;
        this.writer = writer;
        this.runs = runs;
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
     * 下载资产、解析并写入固定版本。任务行已带版本标识时说明版本步骤完成过，重试不重复建版本。
     *
     * @param job 任务
     */
    @Override
    public void execute(Job job) {
        if (job.getDocumentVersionId() != null) {
            log.info("skip finished extraction step jobId={} versionId={}", job.getId(), job.getDocumentVersionId());
            return;
        }
        ProcessingRun run = ProcessingRun.builder()
                .id(UUID.randomUUID())
                .jobId(job.getId())
                .provider("builtin")
                .modelId("text-v1")
                .status("running")
                .createdAt(Instant.now())
                .build();
        runs.save(run);
        try {
            UploadSession session = uploads.requireCompleted(job.getDocumentId());
            Asset asset = assets.findById(session.getAssetId()).orElseThrow();
            byte[] bytes = store.getObject(asset.getObjectKey());
            DocumentParser.ParseResult parsed = parser.parse(new DocumentParser.ParseRequest(
                    asset.getObjectKey(), asset.getOriginalFilename(), asset.getContentType(), bytes));
            if (!"succeeded".equals(parsed.status())) {
                throw new IllegalStateException(parsed.error());
            }
            UUID versionId = writer.writeVersion(
                    job,
                    job.getDocumentId(),
                    asset.getId(),
                    asset.getChecksumSha256(),
                    "text-v1",
                    splitUnits(parsed.text()));
            run.setNew(false);
            run.setInputDocumentVersionId(versionId);
            run.setStatus("succeeded");
            run.setFinishedAt(Instant.now());
            runs.save(run);
            log.info("extracted content jobId={} versionId={}", job.getId(), versionId);
        } catch (Exception ex) {
            run.setNew(false);
            run.setStatus("failed");
            run.setFinishedAt(Instant.now());
            runs.save(run);
            throw ex;
        }
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
