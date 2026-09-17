package com.ontotrace.document.parser.structure;

import com.ontotrace.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结构方案与提取预览。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api")
public class StructureSchemeController {

    private final DocumentStructureService documentStructureService;

    /**
     * 创建控制器。
     *
     * @param documentStructureService 结构服务
     */
    public StructureSchemeController(DocumentStructureService documentStructureService) {
        this.documentStructureService = documentStructureService;
    }

    /**
     * 预置结构方案，名称按体例。
     *
     * @return 方案列表
     */
    @GetMapping("/structure-schemes")
    public List<SchemeResponse> list() {
        return documentStructureService.listSchemes().stream().map(SchemeResponse::from).toList();
    }

    /**
     * 试解析，不写文档版本。
     *
     * @param user 当前用户
     * @param documentId 文档标识
     * @param request 方案
     * @return 预览
     */
    @PostMapping("/documents/{documentId}/extraction-previews")
    public PreviewResponse preview(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID documentId,
            @RequestBody StructureSchemeRequest request) {
        return PreviewResponse.from(documentStructureService.preview(user, documentId, request));
    }

    /**
     * 方案响应。
     *
     * @param id 标识
     * @param name 体例名
     * @param profile 完整规则
     */
    public record SchemeResponse(String id, String name, StructureProfile profile) {
        static SchemeResponse from(StructureProfile profile) {
            return new SchemeResponse(profile.id(), profile.name(), profile);
        }
    }

    /**
     * 试解析响应。
     *
     * @param acceptable 是否允许确认
     * @param unitCount 段落数
     * @param headingCount 标题数
     * @param tocLineCount 目录行数
     * @param unmatchedHeadings 未识别标题
     * @param unmatchedVolumes 未对照卷
     * @param warnings 诊断
     * @param outline 目录树
     */
    public record PreviewResponse(
            boolean acceptable,
            int unitCount,
            int headingCount,
            int tocLineCount,
            List<String> unmatchedHeadings,
            List<String> unmatchedVolumes,
            List<String> warnings,
            List<OutlineTrees.OutlineNode> outline) {
        static PreviewResponse from(TextStructureParser.ParseResult result) {
            return new PreviewResponse(
                    result.acceptable(),
                    result.units().size(),
                    result.headingCount(),
                    result.tocLineCount(),
                    result.unmatchedHeadings(),
                    result.unmatchedVolumes(),
                    result.warnings(),
                    result.outline());
        }
    }
}
