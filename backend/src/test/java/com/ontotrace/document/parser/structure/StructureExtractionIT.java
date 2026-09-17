package com.ontotrace.document.parser.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ontotrace.document.Document;
import com.ontotrace.document.DocumentService;
import com.ontotrace.document.DocumentVersionRepository;
import com.ontotrace.document.TextUnit;
import com.ontotrace.document.TextUnitRepository;
import com.ontotrace.document.asset.Asset;
import com.ontotrace.document.asset.AssetRepository;
import com.ontotrace.document.asset.S3AssetStore;
import com.ontotrace.document.asset.UploadSession;
import com.ontotrace.document.asset.UploadSessionRepository;
import com.ontotrace.runcontrol.Job;
import com.ontotrace.runcontrol.JobRepository;
import com.ontotrace.runcontrol.JobService;
import com.ontotrace.security.AppUser;
import com.ontotrace.security.AppUserRepository;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.semantic.persistence.EduRepository;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import com.ontotrace.runcontrol.ProcessingRun;
import com.ontotrace.runcontrol.ProcessingRunRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 预览不写版本、确认后写入语义 path、path 前缀过滤、EDU 范围与全书门禁。
 *
 * @author hanbd
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StructureExtractionIT {

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

    @MockitoBean
    S3AssetStore store;

    @Autowired
    DocumentStructureService documentStructureService;

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentVersionRepository documentVersionRepo;

    @Autowired
    TextUnitRepository textUnitRepo;

    @Autowired
    AssetRepository assetRepo;

    @Autowired
    UploadSessionRepository uploadSessionRepo;

    @Autowired
    JobService jobService;

    @Autowired
    JobRepository jobRepo;

    @Autowired
    ProcessingRunRepository processingRunRepo;

    @Autowired
    EduRepository eduRepo;

    @Autowired
    AppUserRepository appUserRepo;

    /**
     * 试解析不增加 document_version；确认提取后 path 为语义路径；前缀过滤只返回该节点。
     */
    @Test
    void previewDoesNotWriteVersionAndConfirmUsesSemanticPath() {
        CurrentUser user = admin();
        Document document = documentService.create(user, "结构预览", null, null);
        seedUpload(document.getId(), user.id(), readSlice("jizhuan-toc-divergent.txt"));
        int before = documentVersionRepo.findByDocumentIdOrderByVersionNoDesc(document.getId()).size();
        TextStructureParser.ParseResult preview = documentStructureService.preview(
                user, document.getId(), new StructureSchemeRequest("jizhuan-toc-divergent", null));
        assertTrue(preview.acceptable(), preview.summary());
        assertEquals(before, documentVersionRepo.findByDocumentIdOrderByVersionNoDesc(document.getId()).size());

        Job job = jobService.submitExtract(
                user, document.getId(), null, new StructureSchemeRequest("jizhuan-toc-divergent", null));
        runToCompletion(job);
        UUID versionId = jobRepo.findById(job.getId()).orElseThrow().getDocumentVersionId();
        assertNotNull(versionId);
        List<TextUnit> units = textUnitRepo.findByDocumentVersionIdOrderBySeqAsc(versionId);
        assertFalse(units.isEmpty());
        assertEquals(
                List.of(
                        "卷一 本纪第一 高祖",
                        "卷一 本纪第一 高祖",
                        "卷二 本纪第二 太宗上",
                        "卷二 本纪第二 太宗上",
                        "卷五十一 列传第一 后妃上"),
                units.stream().map(TextUnit::getPath).toList());
        assertTrue(units.stream().noneMatch(unit -> unit.getPath().matches("p\\d+")));

        List<TextUnit> prefix = textUnitRepo.findByDocumentVersionIdAndPathPrefix(
                versionId, "卷一 本纪第一 高祖", StructurePaths.likeLiteral("卷一 本纪第一 高祖") + "/%");
        assertEquals(2, prefix.size());
        assertTrue(prefix.stream().allMatch(unit -> "卷一 本纪第一 高祖".equals(unit.getPath())));
    }

    /**
     * 无方案时仍空行切段，path 为 pN。
     */
    @Test
    void noSchemeKeepsBlankLinePn() {
        CurrentUser user = admin();
        Document document = documentService.create(user, "玄武门切片", null, null);
        seedUpload(document.getId(), user.id(), "太宗伏兵玄武门。\n\n建成、元吉至临湖殿。".getBytes(StandardCharsets.UTF_8));
        Job job = jobService.submitExtract(user, document.getId(), null, null);
        runToCompletion(job);
        UUID versionId = jobRepo.findById(job.getId()).orElseThrow().getDocumentVersionId();
        List<TextUnit> units = textUnitRepo.findByDocumentVersionIdOrderBySeqAsc(versionId);
        assertEquals(List.of("p1", "p2"), units.stream().map(TextUnit::getPath).toList());
    }

    /**
     * 未勾选时拒绝全文 EDU；path 前缀只覆盖该范围。
     */
    @Test
    void eduRejectsFullDocumentUnlessConfirmed() {
        CurrentUser user = admin();
        Document document = documentService.create(user, "EDU 范围", null, null);
        seedUpload(document.getId(), user.id(), readSlice("biannian-juan-ji-nian.txt"));
        Job extract = jobService.submitExtract(
                user, document.getId(), null, new StructureSchemeRequest("biannian-juan-ji-nian", null));
        runToCompletion(extract);
        UUID versionId = jobRepo.findById(extract.getId()).orElseThrow().getDocumentVersionId();

        assertThrows(
                UnprocessableException.class,
                () -> jobService.submitEdu(user, versionId, null, null, null, false));

        Job scoped = jobService.submitEdu(user, versionId, null, null, "卷第一/周纪一/威烈王二十三年", false);
        runToCompletion(scoped);
        ProcessingRun run = processingRunRepo.findFirstByJobIdOrderByCreatedAtDesc(scoped.getId()).orElseThrow();
        assertEquals("path:卷第一/周纪一/威烈王二十三年", run.getInputRange());
        assertEquals(2, eduRepo.findVisible(versionId).size());

        Job other = jobService.submitEdu(user, versionId, null, null, "卷第二/秦纪一/昭襄王五十二年", false);
        runToCompletion(other);
        assertEquals(3, eduRepo.findVisible(versionId).size());
    }

    private CurrentUser admin() {
        AppUser user = appUserRepo.findByUsername("admin").orElseThrow();
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getPlatformRole());
    }

    private void runToCompletion(Job job) {
        Job claimed = jobService.claim("it-worker");
        assertNotNull(claimed);
        assertEquals(job.getId(), claimed.getId());
        jobService.execute(claimed);
        assertEquals("succeeded", jobRepo.findById(job.getId()).orElseThrow().getStatus());
    }

    private void seedUpload(UUID documentId, UUID userId, byte[] bytes) {
        when(store.getObject(any())).thenReturn(bytes);
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .objectKey("it/" + UUID.randomUUID())
                .originalFilename("book.txt")
                .contentType("text/plain")
                .sizeBytes((long) bytes.length)
                .checksumSha256(S3AssetStore.sha256(bytes))
                .createdAt(Instant.now())
                .build();
        assetRepo.save(asset);
        uploadSessionRepo.save(UploadSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .documentId(documentId)
                .objectKey(asset.getObjectKey())
                .originalFilename(asset.getOriginalFilename())
                .declaredContentType("text/plain")
                .declaredSizeBytes(asset.getSizeBytes())
                .declaredChecksumSha256(asset.getChecksumSha256())
                .status("completed")
                .assetId(asset.getId())
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build());
    }

    private static byte[] readSlice(String name) {
        try (InputStream in = StructureExtractionIT.class.getResourceAsStream("/structure/" + name)) {
            if (in == null) {
                throw new IllegalStateException("缺少切片 " + name);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
