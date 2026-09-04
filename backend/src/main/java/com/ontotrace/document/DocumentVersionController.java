package com.ontotrace.document;

import com.ontotrace.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档版本与文本单元接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/document-versions")
public class DocumentVersionController {

    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final TextUnitRepository textUnits;

    /**
     * 创建控制器。
     *
     * @param documents 文档服务
     * @param versions 版本文仓
     * @param textUnits 文本单元仓储
     */
    public DocumentVersionController(
            DocumentService documents, DocumentVersionRepository versions, TextUnitRepository textUnits) {
        this.documents = documents;
        this.versions = versions;
        this.textUnits = textUnits;
    }

    /**
     * 读取固定版本。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return 版本
     */
    @GetMapping("/{versionId}")
    public VersionResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
        DocumentVersion version = versions.findById(versionId).orElseThrow();
        documents.requireView(user, version.getDocumentId());
        return VersionResponse.from(version);
    }

    /**
     * 读取文本单元。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return 文本单元
     */
    @GetMapping("/{versionId}/text-units")
    public List<TextUnitResponse> textUnits(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
        DocumentVersion version = versions.findById(versionId).orElseThrow();
        documents.requireView(user, version.getDocumentId());
        return textUnits.findByDocumentVersionIdOrderBySeqAsc(versionId).stream()
                .map(TextUnitResponse::from)
                .toList();
    }

    /**
     * 版本响应。
     *
     * @param id 版本标识
     * @param documentId 文档标识
     * @param versionNo 版本号
     * @param parserId 解析器
     * @param createdAt 创建时间
     */
    public record VersionResponse(String id, String documentId, int versionNo, String parserId, Instant createdAt) {
        static VersionResponse from(DocumentVersion version) {
            return new VersionResponse(
                    version.getId().toString(),
                    version.getDocumentId().toString(),
                    version.getVersionNo(),
                    version.getParserId(),
                    version.getCreatedAt());
        }
    }

    /**
     * 文本单元响应。
     *
     * @param id 标识
     * @param seq 顺序
     * @param path 结构路径
     * @param displayText 展示文本
     */
    public record TextUnitResponse(String id, int seq, String path, String displayText) {
        static TextUnitResponse from(TextUnit unit) {
            return new TextUnitResponse(unit.getId().toString(), unit.getSeq(), unit.getPath(), unit.getDisplayText());
        }
    }
}
