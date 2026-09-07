package com.ontotrace.runcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ontotrace.document.Document;
import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersion;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.asset.Asset;
import com.ontotrace.document.asset.AssetRepository;
import com.ontotrace.security.AppUser;
import com.ontotrace.security.AppUserRepository;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.semantic.Edu;
import com.ontotrace.semantic.persistence.EduRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * EDU 覆盖式重写、任务幂等与处理运行记录的集成检查。
 *
 * @author hanbd
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ExtractEduPersistenceIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("ontotrace")
            .withUsername("test")
            .withPassword("test");

    /**
     * 注入测试库连接。
     *
     * @param registry 动态属性
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JobService jobService;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    DocumentService documents;

    @Autowired
    DocumentVersionRepository versions;

    @Autowired
    TextUnitRepository textUnits;

    @Autowired
    AssetRepository assets;

    @Autowired
    EduRepository edus;

    @Autowired
    ProcessingRunRepository runs;

    @Autowired
    AppUserRepository users;

    /**
     * 全文重跑为覆盖式重写：旧 EDU 物理删除、总行数等于新一轮条数；
     * 未完成任务重复提交复用同一任务，终态后重跑新建。
     */
    @Test
    void rerunReplacesByRewrite() {
        CurrentUser user = admin();
        Document document = documents.create(user, "覆盖重写测试", null, null);
        UUID versionId = newVersion(document.getId(), "太宗率长孙无忌伏兵玄武门。", "皇太子建成、齐王元吉谋害太宗。");

        Job first = jobService.submitEdu(user, versionId, null, null);
        assertEquals(first.getId(), jobService.submitEdu(user, versionId, null, null).getId());
        runToCompletion(first);
        assertEquals(2, edus.findVisible(versionId).size());

        Job second = jobService.submitEdu(user, versionId, null, null);
        assertNotEquals(first.getId(), second.getId());
        runToCompletion(second);

        List<Edu> all = versionEdus(versionId);
        assertEquals(2, all.size());
        assertEquals(0, all.stream().filter(edu -> "superseded".equals(edu.getStatus())).count());
        assertEquals(2, edus.findVisible(versionId).size());
    }

    /**
     * 局部重跑只删除引用该文本单元的 EDU，未涉及单元的结果保留。
     */
    @Test
    void partialRerunDeletesOnlyCitedEdus() {
        CurrentUser user = admin();
        Document document = documents.create(user, "局部重跑测试", null, null);
        UUID versionId = newVersion(document.getId(), "太宗率长孙无忌伏兵玄武门。", "皇太子建成、齐王元吉谋害太宗。");

        Job full = jobService.submitEdu(user, versionId, null, null);
        runToCompletion(full);
        assertEquals(2, edus.findVisible(versionId).size());

        TextUnit secondUnit = textUnits.findByDocumentVersionIdOrderBySeqAsc(versionId).get(1);
        Job partial = jobService.submitEdu(user, versionId, null, secondUnit.getId());
        runToCompletion(partial);

        List<Edu> all = versionEdus(versionId);
        assertEquals(2, all.size());
        assertEquals(0, all.stream().filter(edu -> "superseded".equals(edu.getStatus())).count());
        assertEquals(2, edus.findVisible(versionId).size());
    }

    /**
     * 抽取运行记录模型、提示版本、输入范围与词元，EDU 回填运行标识。
     */
    @Test
    void recordsProcessingRun() {
        CurrentUser user = admin();
        Document document = documents.create(user, "运行记录测试", null, null);
        UUID versionId = newVersion(document.getId(), "太宗率长孙无忌伏兵玄武门。");

        Job job = jobService.submitEdu(user, versionId, null, null);
        runToCompletion(job);

        ProcessingRun run = runs.findFirstByJobIdOrderByCreatedAtDesc(job.getId()).orElseThrow();
        assertEquals("succeeded", run.getStatus());
        assertEquals(versionId, run.getInputDocumentVersionId());
        assertEquals("all", run.getInputRange());
        assertEquals("neighbor-1+target", run.getContextStrategy());
        assertEquals("edu-generate-v1", run.getPromptVersion());
        assertEquals("stub", run.getModelId());
        assertNotNull(run.getFinishedAt());
        List<Edu> visible = edus.findVisible(versionId);
        assertEquals(1, visible.size());
        assertEquals(run.getId(), visible.getFirst().getProcessingRunId());
    }

    private CurrentUser admin() {
        AppUser user = users.findByUsername("admin").orElseThrow();
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getPlatformRole());
    }

    private void runToCompletion(Job job) {
        Job claimed = jobService.claim("it-worker");
        assertNotNull(claimed);
        assertEquals(job.getId(), claimed.getId());
        jobService.execute(claimed);
        Job finished = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals("succeeded", finished.getStatus());
    }

    private UUID newVersion(UUID documentId, String... unitTexts) {
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .objectKey("it/" + UUID.randomUUID())
                .originalFilename("it.txt")
                .contentType("text/plain")
                .sizeBytes(16L)
                .checksumSha256(UUID.randomUUID().toString().replace("-", ""))
                .createdAt(Instant.now())
                .build();
        assets.save(asset);
        UUID versionId = UUID.randomUUID();
        versions.save(DocumentVersion.builder()
                .id(versionId)
                .documentId(documentId)
                .assetId(asset.getId())
                .versionNo(1)
                .contentFingerprint(asset.getChecksumSha256())
                .parserId("text-v1")
                .createdAt(Instant.now())
                .build());
        int seq = 1;
        for (String text : unitTexts) {
            textUnits.save(TextUnit.builder()
                    .id(UUID.randomUUID())
                    .documentVersionId(versionId)
                    .seq(seq)
                    .path("p" + seq)
                    .displayText(text)
                    .build());
            seq++;
        }
        return versionId;
    }

    private List<Edu> versionEdus(UUID versionId) {
        return StreamSupport.stream(edus.findAll().spliterator(), false)
                .filter(edu -> versionId.equals(edu.getDocumentVersionId()))
                .toList();
    }
}
