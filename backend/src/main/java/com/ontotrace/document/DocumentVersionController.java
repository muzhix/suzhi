package com.ontotrace.document;

import com.ontotrace.document.parser.structure.OutlineTrees;
import com.ontotrace.document.parser.structure.StructurePaths;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.web.ApiExceptionHandler.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档版本与文本单元接口。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/document-versions")
public class DocumentVersionController {

    private final DocumentService documentService;
    private final DocumentVersionRepository documentVersionRepo;
    private final TextUnitRepository textUnitRepo;

    /**
     * 创建控制器。
     *
     * @param documentService 文档服务
     * @param documentVersionRepo 版本文仓
     * @param textUnitRepo 文本单元仓储
     */
    public DocumentVersionController(
            DocumentService documentService, DocumentVersionRepository documentVersionRepo, TextUnitRepository textUnitRepo) {
        this.documentService = documentService;
        this.documentVersionRepo = documentVersionRepo;
        this.textUnitRepo = textUnitRepo;
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
        DocumentVersion version = requireVersion(user, versionId);
        return VersionResponse.from(version);
    }

    /**
     * 由 path 聚合目录树，不建篇章表。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @return 目录树
     */
    @GetMapping("/{versionId}/outline")
    public OutlineResponse outline(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID versionId) {
        requireVersion(user, versionId);
        List<OutlineTrees.OutlineNode> nodes = OutlineTrees.fromPathNotes(
                textUnitRepo.findPathNotesByDocumentVersionId(versionId).stream()
                        .map(row -> new OutlineTrees.PathNote(row.path(), row.ceYear(), row.ganzhi()))
                        .toList());
        int unitCount = nodes.stream().mapToInt(OutlineTrees.OutlineNode::unitCount).sum();
        return new OutlineResponse(nodes, unitCount);
    }

    /**
     * 读取文本单元，可按 path 前缀过滤。
     *
     * @param user 当前用户
     * @param versionId 版本标识
     * @param pathPrefix 结构路径前缀
     * @return 文本单元
     */
    @GetMapping("/{versionId}/text-units")
    public List<TextUnitResponse> textUnits(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID versionId,
            @RequestParam(required = false) String pathPrefix) {
        requireVersion(user, versionId);
        List<TextUnit> rows;
        if (pathPrefix == null || pathPrefix.isBlank()) {
            rows = textUnitRepo.findByDocumentVersionIdOrderBySeqAsc(versionId);
        } else {
            rows = textUnitRepo.findByDocumentVersionIdAndPathPrefix(
                    versionId, pathPrefix, StructurePaths.likeLiteral(pathPrefix) + "/%");
        }
        return rows.stream().map(TextUnitResponse::from).toList();
    }

    private DocumentVersion requireVersion(CurrentUser user, UUID versionId) {
        DocumentVersion version = documentVersionRepo.findById(versionId).orElseThrow(() -> new NotFoundException("文档版本不存在"));
        documentService.requireView(user, version.getDocumentId());
        return version;
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
     * 目录树响应。
     *
     * @param nodes 根层节点
     * @param unitCount 文本单元总数
     */
    public record OutlineResponse(List<OutlineTrees.OutlineNode> nodes, int unitCount) {}

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
