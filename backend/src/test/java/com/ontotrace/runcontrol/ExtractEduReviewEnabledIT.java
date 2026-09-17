package com.ontotrace.runcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * review-enabled=true 时模型复核链路的集成检查：复核结果写回 EDU，运行记录复核模型与提示版本。
 *
 * @author hanbd
 */
@SpringBootTest(properties = "ontotrace.ai.review-enabled=true")
@ActiveProfiles("test")
@Testcontainers
class ExtractEduReviewEnabledIT {

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
    JobRepository jobRepo;

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentVersionRepository documentVersionRepo;

    @Autowired
    TextUnitRepository textUnitRepo;

    @Autowired
    AssetRepository assetRepo;

    @Autowired
    EduRepository eduRepo;

    @Autowired
    ProcessingRunRepository processingRunRepo;

    @Autowired
    AppUserRepository appUserRepo;

    /**
     * stub 复核恒通过：EDU 记为 passed，运行记录回填复核模型与提示版本。
     */
    @Test
    void reviewResultPersistedWhenEnabled() {
        CurrentUser user = admin();
        Document document = documentService.create(user, "复核开启测试", null, null);
        UUID versionId = newVersion(document.getId(), "太宗率长孙无忌伏兵玄武门。");

        Job job = jobService.submitEdu(user, versionId, null, null);
        Job claimed = jobService.claim("it-worker");
        assertNotNull(claimed);
        assertEquals(job.getId(), claimed.getId());
        jobService.execute(claimed);
        assertEquals("succeeded", jobRepo.findById(job.getId()).orElseThrow().getStatus());

        List<Edu> visible = eduRepo.findVisible(versionId);
        assertEquals(1, visible.size());
        Edu edu = visible.getFirst();
        assertEquals("active", edu.getStatus());
        assertEquals("passed", edu.getReviewResult());
        assertEquals("stub pass", edu.getReviewNotes());

        ProcessingRun run = processingRunRepo.findFirstByJobIdOrderByCreatedAtDesc(job.getId()).orElseThrow();
        assertEquals("stub", run.getReviewModelId());
        assertEquals("edu-review-v1", run.getReviewPromptVersion());
    }

    private CurrentUser admin() {
        AppUser user = appUserRepo.findByUsername("admin").orElseThrow();
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getPlatformRole());
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
        assetRepo.save(asset);
        UUID versionId = UUID.randomUUID();
        documentVersionRepo.save(DocumentVersion.builder()
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
            textUnitRepo.save(TextUnit.builder()
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
}
