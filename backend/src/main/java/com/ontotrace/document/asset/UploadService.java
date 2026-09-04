package com.ontotrace.document.asset;

import com.ontotrace.document.DocumentService;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预签名上传与完成校验。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class UploadService {

    private static final Set<String> ALLOWED_EXT = Set.of("txt", "md", "markdown");

    private final UploadSessionRepository sessions;
    private final AssetRepository assets;
    private final S3AssetStore store;
    private final DocumentService documents;

    /**
     * 创建服务。
     *
     * @param sessions 上传会话仓储
     * @param assets 资产仓储
     * @param store 对象存储
     * @param documents 文档服务
     */
    public UploadService(
            UploadSessionRepository sessions, AssetRepository assets, S3AssetStore store, DocumentService documents) {
        this.sessions = sessions;
        this.assets = assets;
        this.store = store;
        this.documents = documents;
    }

    /**
     * 创建上传会话并签发预签名 PUT。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param filename 文件名
     * @param sizeBytes 声明大小
     * @param contentType 浏览器声明类型
     * @param checksumSha256 声明校验和
     * @return 上传会话
     */
    @Transactional
    public UploadCreated create(
            CurrentUser user,
            UUID documentId,
            String filename,
            long sizeBytes,
            String contentType,
            String checksumSha256) {
        documents.requireEdit(user, documentId);
        String ext = extension(filename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new UnprocessableException("B0 只接受 TXT 或 Markdown");
        }
        UUID uploadId = UUID.randomUUID();
        String objectKey = "uploads/" + user.id() + "/" + uploadId + "/" + sanitize(filename);
        Instant now = Instant.now();
        sessions.save(UploadSession.builder()
                .id(uploadId)
                .userId(user.id())
                .documentId(documentId)
                .objectKey(objectKey)
                .originalFilename(filename)
                .declaredContentType(contentType == null ? "application/octet-stream" : contentType)
                .declaredSizeBytes(sizeBytes)
                .declaredChecksumSha256(checksumSha256.toLowerCase(Locale.ROOT))
                .status("pending")
                .expiresAt(now.plus(20, ChronoUnit.MINUTES))
                .createdAt(now)
                .build());
        String url = store.presignPut(objectKey, "application/octet-stream");
        log.info("created upload uploadId={} documentId={}", uploadId, documentId);
        return new UploadCreated(uploadId, objectKey, url, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /**
     * 完成上传：从对象存储读取并校验大小、扩展名和校验和。
     *
     * @param user 当前用户
     * @param uploadId 上传标识
     * @return 资产标识
     */
    @Transactional
    public UUID complete(CurrentUser user, UUID uploadId) {
        UploadSession session = sessions.findById(uploadId).orElseThrow(() -> new NotFoundException("上传会话不存在"));
        if (!session.getUserId().equals(user.id())) {
            throw new NotFoundException("上传会话不存在");
        }
        documents.requireEdit(user, session.getDocumentId());
        byte[] bytes;
        try {
            bytes = store.getObject(session.getObjectKey());
        } catch (RuntimeException ex) {
            log.warn("failed to read uploaded object uploadId={} key={}", uploadId, session.getObjectKey(), ex);
            throw new UnprocessableException("无法读取已上传对象");
        }
        session.setNew(false);
        String actualSha = S3AssetStore.sha256(bytes);
        if (bytes.length != session.getDeclaredSizeBytes()) {
            fail(session, "大小与声明不一致");
        }
        if (!actualSha.equalsIgnoreCase(session.getDeclaredChecksumSha256())) {
            fail(session, "校验和与声明不一致");
        }
        String ext = extension(session.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            fail(session, "媒体类型不在允许列表");
        }
        if (!looksLikeText(bytes)) {
            fail(session, "文件特征不是文本");
        }
        UUID assetId = UUID.randomUUID();
        assets.save(Asset.builder()
                .id(assetId)
                .objectKey(session.getObjectKey())
                .originalFilename(session.getOriginalFilename())
                .contentType(ext.equals("md") || ext.equals("markdown") ? "text/markdown" : "text/plain")
                .sizeBytes((long) bytes.length)
                .checksumSha256(actualSha)
                .createdAt(Instant.now())
                .build());
        session.setStatus("completed");
        session.setAssetId(assetId);
        sessions.save(session);
        log.info("completed upload uploadId={} assetId={}", uploadId, assetId);
        return assetId;
    }

    /**
     * 文档最近一次完成上传的资产。
     *
     * @param documentId 文档标识
     * @return 会话
     */
    public UploadSession requireCompleted(UUID documentId) {
        return sessions.findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(documentId, "completed")
                .orElseThrow(() -> new UnprocessableException("请先完成 TXT/Markdown 上传"));
    }

    private void fail(UploadSession session, String message) {
        session.setStatus("failed");
        sessions.save(session);
        throw new UnprocessableException(message);
    }

    private static boolean looksLikeText(byte[] bytes) {
        int sample = Math.min(bytes.length, 512);
        for (int i = 0; i < sample; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 创建上传结果。
     *
     * @param uploadId 上传标识
     * @param objectKey 对象键
     * @param presignedPutUrl 预签名 URL
     * @param expiresAt 到期时间
     */
    public record UploadCreated(UUID uploadId, String objectKey, String presignedPutUrl, Instant expiresAt) {}
}
