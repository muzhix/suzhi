package com.ontotrace.retrieval;

import com.ontotrace.document.Document;
import com.ontotrace.security.CurrentUser;
import com.ontotrace.semantic.persistence.EduRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 带权限过滤的原文 / EDU 基础检索。
 *
 * @author hanbd
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService search;

    /**
     * 创建控制器。
     *
     * @param search 检索服务
     */
    public SearchController(SearchService search) {
        this.search = search;
    }

    /**
     * 检索。无 EDU 时仍返回原文命中。
     *
     * @param user 当前用户
     * @param request 查询
     * @return 命中
     */
    @PostMapping
    public SearchResponse search(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SearchRequest request) {
        return search.search(user, request.query());
    }

    /**
     * 检索请求。
     *
     * @param query 关键词
     */
    public record SearchRequest(@NotBlank String query) {}

    /**
     * 检索响应。
     *
     * @param textHits 原文命中
     * @param eduHits EDU 命中
     */
    public record SearchResponse(List<TextHit> textHits, List<EduHit> eduHits) {}

    /**
     * 原文命中。
     *
     * @param textUnitId 文本单元
     * @param documentId 文档
     * @param documentVersionId 版本
     * @param snippet 摘要
     */
    public record TextHit(String textUnitId, String documentId, String documentVersionId, String snippet) {}

    /**
     * EDU 命中。
     *
     * @param eduId EDU
     * @param documentVersionId 版本
     * @param text 自足表述
     * @param status 状态
     */
    public record EduHit(String eduId, String documentVersionId, String text, String status) {}
}

/**
 * 原文检索仓储。
 *
 * @author hanbd
 */
interface TextSearchRepository extends Repository<Document, UUID> {

    /**
     * 在有权文档版本中检索文本单元。
     *
     * @param userId 用户标识
     * @param query 关键词
     * @return 命中
     */
    @Query(
            """
            SELECT tu.id AS text_unit_id, dv.document_id AS document_id, tu.document_version_id AS document_version_id,
                   left(tu.display_text, 180) AS snippet
            FROM text_unit tu
            JOIN document_version dv ON dv.id = tu.document_version_id
            JOIN document_acl a ON a.document_id = dv.document_id AND a.user_id = :userId
            WHERE tu.display_text ILIKE '%' || :query || '%'
            ORDER BY tu.seq
            LIMIT 50
            """)
    List<TextHitRow> searchText(UUID userId, String query);

    /**
     * 原文命中行。
     *
     * @param textUnitId 文本单元
     * @param documentId 文档
     * @param documentVersionId 版本
     * @param snippet 摘要
     */
    record TextHitRow(UUID textUnitId, UUID documentId, UUID documentVersionId, String snippet) {}
}

/**
 * 检索服务。
 *
 * @author hanbd
 */
@Service
class SearchService {

    private final TextSearchRepository texts;
    private final EduRepository edus;

    SearchService(TextSearchRepository texts, EduRepository edus) {
        this.texts = texts;
        this.edus = edus;
    }

    SearchController.SearchResponse search(CurrentUser user, String query) {
        List<SearchController.TextHit> textHits = texts.searchText(user.id(), query).stream()
                .map(row -> new SearchController.TextHit(
                        row.textUnitId().toString(),
                        row.documentId().toString(),
                        row.documentVersionId().toString(),
                        row.snippet()))
                .toList();
        List<SearchController.EduHit> eduHits = edus.searchAccessible(user.id(), query).stream()
                .map(edu -> new SearchController.EduHit(
                        edu.getId().toString(), edu.getDocumentVersionId().toString(), edu.getText(), edu.getStatus()))
                .toList();
        return new SearchController.SearchResponse(textHits, eduHits);
    }
}
