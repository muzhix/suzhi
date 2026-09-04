package com.ontotrace.semantic.persistence;

import com.ontotrace.semantic.Edu;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * EDU 仓储。
 *
 * @author hanbd
 */
public interface EduRepository extends ListCrudRepository<Edu, UUID> {

    /**
     * 读取固定版本中未拒绝、未被替代的 EDU。
     *
     * @param documentVersionId 版本标识
     * @param excluded 排除状态
     * @return EDU 列表
     */
    @Query(
            """
            SELECT id, document_version_id, type, text, predicate, time_value, time_source_form, time_precision,
                   status, revision, location_precision, used_external_knowledge, review_result, review_notes,
                   processing_run_id, supersedes_edu_id, created_at, updated_at
            FROM edu
            WHERE document_version_id = :documentVersionId
              AND status NOT IN ('rejected', 'superseded')
            ORDER BY created_at
            """)
    List<Edu> findVisible(UUID documentVersionId);

    /**
     * 是否已有该版本的 EDU。
     *
     * @param documentVersionId 版本标识
     * @return 存在返回 true
     */
    boolean existsByDocumentVersionId(UUID documentVersionId);

    /**
     * 删除固定版本下全部 EDU。
     *
     * @param documentVersionId 版本标识
     */
    @Modifying
    @Query("DELETE FROM edu WHERE document_version_id = :documentVersionId")
    void deleteByDocumentVersionId(UUID documentVersionId);

    /**
     * 删除指定 EDU。
     *
     * @param eduIds EDU 标识
     */
    @Modifying
    @Query("DELETE FROM edu WHERE id IN (:eduIds)")
    void deleteByIdIn(List<UUID> eduIds);

    /**
     * 供检索使用的可见 EDU。
     *
     * @param userId 用户标识
     * @param query 关键词
     * @return EDU 列表
     */
    @Query(
            """
            SELECT e.id, e.document_version_id, e.type, e.text, e.predicate, e.time_value, e.time_source_form,
                   e.time_precision, e.status, e.revision, e.location_precision, e.used_external_knowledge,
                   e.review_result, e.review_notes, e.processing_run_id, e.supersedes_edu_id, e.created_at, e.updated_at
            FROM edu e
            JOIN document_version dv ON dv.id = e.document_version_id
            JOIN document_acl a ON a.document_id = dv.document_id AND a.user_id = :userId
            WHERE e.status NOT IN ('rejected', 'superseded')
              AND e.text ILIKE '%' || :query || '%'
            ORDER BY e.created_at DESC
            LIMIT 50
            """)
    List<Edu> searchAccessible(UUID userId, String query);
}
