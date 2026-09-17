package com.ontotrace.document;

import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import com.ontotrace.web.LikeQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档用例。
 *
 * @author hanbd
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentRepository documentRepo;
    private final DocumentVersionRepository documentVersionRepo;

    /**
     * 创建服务。
     *
     * @param documentRepo 文档仓储
     * @param documentVersionRepo 版本文仓
     */
    public DocumentService(DocumentRepository documentRepo, DocumentVersionRepository documentVersionRepo) {
        this.documentRepo = documentRepo;
        this.documentVersionRepo = documentVersionRepo;
    }

    /**
     * 创建文档，创建者成为所有者。
     *
     * @param user 当前用户
     * @param title 文档名称
     * @param authors 作者
     * @param materialType 材料类型
     * @return 文档
     */
    @Transactional
    public Document create(CurrentUser user, String title, String authors, String materialType) {
        Instant now = Instant.now();
        Document document = Document.builder()
                .id(UUID.randomUUID())
                .title(title)
                .authors(authors)
                .materialType(materialType)
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build();
        documentRepo.save(document);
        documentRepo.insertAcl(document.getId(), user.id(), "owner");
        log.info("created document documentId={} userId={}", document.getId(), user.id());
        return document;
    }

    /**
     * 更新文档名称和作者。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param title 文档名称
     * @param authors 作者
     * @return 文档
     */
    @Transactional
    public Document update(CurrentUser user, UUID documentId, String title, String authors) {
        requireEdit(user, documentId);
        Document document = get(user, documentId);
        document.setNew(false);
        document.setTitle(title);
        document.setAuthors(authors);
        document.setUpdatedAt(Instant.now());
        documentRepo.save(document);
        log.info("updated document documentId={}", documentId);
        return document;
    }

    /**
     * 分页列出有权文档。
     *
     * @param user 当前用户
     * @param q 名称或作者关键字
     * @param page 页码，从 0 起
     * @param size 每页条数
     * @return 文档列表
     */
    public List<Document> list(CurrentUser user, String q, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return documentRepo.findAccessible(user.id(), LikeQuery.contains(q), safeSize, safePage * safeSize);
    }

    /**
     * 统计有权文档数。
     *
     * @param user 当前用户
     * @param q 名称或作者关键字
     * @return 总数
     */
    public long count(CurrentUser user, String q) {
        return documentRepo.countAccessible(user.id(), LikeQuery.contains(q));
    }

    /**
     * 读取有权文档。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @return 文档
     */
    public Document get(CurrentUser user, UUID documentId) {
        return documentRepo.findAccessible(documentId, user.id()).orElseThrow(() -> new NotFoundException("文档不存在或无权访问"));
    }

    /**
     * 最新文档版本标识。
     *
     * @param documentId 文档标识
     * @return 版本标识，尚未提取时为空
     */
    public UUID latestVersionId(UUID documentId) {
        return documentVersionRepo.findFirstByDocumentIdOrderByVersionNoDesc(documentId)
                .map(DocumentVersion::getId)
                .orElse(null);
    }

    /**
     * 要求查看权限。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     */
    public void requireView(CurrentUser user, UUID documentId) {
        if (!documentRepo.canView(documentId, user.id())) {
            throw new AccessDeniedException("无权访问该文档");
        }
    }

    /**
     * 要求编辑权限。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     */
    public void requireEdit(CurrentUser user, UUID documentId) {
        if (!documentRepo.canEdit(documentId, user.id())) {
            throw new AccessDeniedException("需要文档编辑权限");
        }
    }
}
