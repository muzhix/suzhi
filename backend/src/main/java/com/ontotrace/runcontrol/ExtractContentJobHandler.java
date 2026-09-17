package com.ontotrace.runcontrol;

import com.ontotrace.document.asset.Asset;
import com.ontotrace.document.asset.AssetRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.document.asset.UploadService;
import com.ontotrace.document.asset.UploadSession;
import com.ontotrace.document.parser.DocumentParser;
import com.ontotrace.document.parser.TextDocumentParser;
import com.ontotrace.document.parser.structure.ExtractJobPayload;
import com.ontotrace.document.parser.structure.StructureJson;
import com.ontotrace.document.parser.structure.StructureProfile;
import com.ontotrace.document.parser.structure.TextStructureParser;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TXT/Markdown 显式提取，生成不可变版本和文本单元。S3 下载与解析在数据库事务外执行，
 * 版本写入由 {@link ExtractContentWriter} 以短事务完成，运行信息记录到处理运行。
 * 有结构方案时写入语义 path；无方案时保持空行切段 {@code pN}。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class ExtractContentJobHandler implements JobHandler {

    private final UploadService uploadService;
    private final AssetRepository assetRepo;
    private final S3AssetStore store;
    private final TextDocumentParser parser;
    private final TextStructureParser structureParser;
    private final ExtractContentWriter writer;
    private final ProcessingRunRepository processingRunRepo;
    private final JobRepository jobRepo;

    /**
     * 创建处理器。
     *
     * @param uploadService 上传服务
     * @param assetRepo 资产仓储
     * @param store 对象存储
     * @param parser 文本解析器
     * @param structureParser 结构解析器
     * @param writer 版本写入器
     * @param processingRunRepo 处理运行仓储
     * @param jobRepo 任务仓储
     */
    public ExtractContentJobHandler(
            UploadService uploadService,
            AssetRepository assetRepo,
            S3AssetStore store,
            TextDocumentParser parser,
            TextStructureParser structureParser,
            ExtractContentWriter writer,
            ProcessingRunRepository processingRunRepo,
            JobRepository jobRepo) {
        this.uploadService = uploadService;
        this.assetRepo = assetRepo;
        this.store = store;
        this.parser = parser;
        this.structureParser = structureParser;
        this.writer = writer;
        this.processingRunRepo = processingRunRepo;
        this.jobRepo = jobRepo;
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
        processingRunRepo.save(run);
        try {
            UploadSession session = uploadService.requireCompleted(job.getDocumentId());
            Asset asset = assetRepo.findById(session.getAssetId()).orElseThrow();
            byte[] bytes = store.getObject(asset.getObjectKey());
            DocumentParser.ParseResult parsed = parser.parse(new DocumentParser.ParseRequest(
                    asset.getObjectKey(), asset.getOriginalFilename(), asset.getContentType(), bytes));
            if (!"succeeded".equals(parsed.status())) {
                throw new IllegalStateException(parsed.error());
            }
            StructureProfile profile = loadProfile(job);
            List<TextStructureParser.Unit> units;
            String parserId = "text-v1";
            if (profile == null) {
                units = splitUnits(parsed.text());
            } else {
                TextStructureParser.ParseResult structured = structureParser.parse(parsed.text(), profile);
                if (!structured.acceptable()) {
                    throw new UnprocessableException("结构识别过差，未写入版本：" + structured.summary());
                }
                units = structured.units();
                parserId = "text-structure-v1";
                processingRunRepo.setParameters(run.getId(), StructureJson.write(profile));
            }
            UUID versionId = writer.writeVersion(
                    job, job.getDocumentId(), asset.getId(), asset.getChecksumSha256(), parserId, units);
            run.setNew(false);
            run.setInputDocumentVersionId(versionId);
            run.setStatus("succeeded");
            run.setFinishedAt(Instant.now());
            processingRunRepo.save(run);
            log.info(
                    "extracted content jobId={} versionId={} scheme={} units={}",
                    job.getId(),
                    versionId,
                    profile == null ? "none" : profile.id(),
                    units.size());
        } catch (Exception ex) {
            run.setNew(false);
            run.setStatus("failed");
            run.setFinishedAt(Instant.now());
            processingRunRepo.save(run);
            throw ex;
        }
    }

    private StructureProfile loadProfile(Job job) {
        String json = jobRepo.findPayload(job.getId()).orElse(null);
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        ExtractJobPayload payload = StructureJson.read(json, ExtractJobPayload.class);
        if (payload == null || payload.profile() == null) {
            return null;
        }
        return payload.profile();
    }

    private static List<TextStructureParser.Unit> splitUnits(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<TextStructureParser.Unit> units = new ArrayList<>();
        int seq = 1;
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                units.add(new TextStructureParser.Unit("p" + seq, trimmed));
                seq++;
            }
        }
        if (units.isEmpty()) {
            units.add(new TextStructureParser.Unit("p1", text.trim()));
        }
        return units;
    }
}
