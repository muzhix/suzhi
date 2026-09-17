package com.ontotrace.document;

import com.ontotrace.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档元数据接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 创建控制器。
     *
     * @param documentService 文档服务
     */
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 分页列出文档。
     *
     * @param user 当前用户
     * @param q 文档名称或作者关键字
     * @param page 页码，从 0 起
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping
    public DocumentPageResponse list(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<DocumentResponse> items = documentService.list(user, q, safePage, safeSize).stream()
                .map(document -> DocumentResponse.from(document, documentService.latestVersionId(document.getId())))
                .toList();
        return new DocumentPageResponse(items, documentService.count(user, q), safePage, safeSize);
    }

    /**
     * 创建文档。
     *
     * @param user 当前用户
     * @param request 创建请求
     * @return 文档
     */
    @PostMapping
    public DocumentResponse create(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateDocumentRequest request) {
        return DocumentResponse.from(
                documentService.create(user, request.title(), request.authors(), request.materialType()), null);
    }

    /**
     * 读取文档。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @return 文档
     */
    @GetMapping("/{documentId}")
    public DocumentResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID documentId) {
        Document document = documentService.get(user, documentId);
        return DocumentResponse.from(document, documentService.latestVersionId(documentId));
    }

    /**
     * 更新文档基本信息。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param request 更新请求
     * @return 文档
     */
    @PutMapping("/{documentId}")
    public DocumentResponse update(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID documentId,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return DocumentResponse.from(
                documentService.update(user, documentId, request.title(), request.authors()),
                documentService.latestVersionId(documentId));
    }

    /**
     * 文档分页响应。
     *
     * @param items 当前页
     * @param total 总数
     * @param page 页码
     * @param size 每页条数
     */
    public record DocumentPageResponse(List<DocumentResponse> items, long total, int page, int size) {}

    /**
     * 创建文档请求。
     *
     * @param title 文档名称
     * @param authors 作者
     * @param materialType 材料类型
     */
    public record CreateDocumentRequest(@NotBlank String title, String authors, String materialType) {}

    /**
     * 更新文档请求。
     *
     * @param title 文档名称
     * @param authors 作者
     */
    public record UpdateDocumentRequest(@NotBlank String title, String authors) {}

    /**
     * 文档响应。
     *
     * @param id 文档标识
     * @param title 文档名称
     * @param authors 作者
     * @param materialType 材料类型
     * @param latestVersionId 最新版本
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public record DocumentResponse(
            String id,
            String title,
            String authors,
            String materialType,
            String latestVersionId,
            Instant createdAt,
            Instant updatedAt) {
        static DocumentResponse from(Document document, UUID latestVersionId) {
            return new DocumentResponse(
                    document.getId().toString(),
                    document.getTitle(),
                    document.getAuthors(),
                    document.getMaterialType(),
                    latestVersionId == null ? null : latestVersionId.toString(),
                    document.getCreatedAt(),
                    document.getUpdatedAt());
        }
    }
}
