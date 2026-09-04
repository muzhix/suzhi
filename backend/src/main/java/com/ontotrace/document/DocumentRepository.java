package com.ontotrace.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 文档仓储。权限过滤用 {@link Query}，不另写 JdbcClient。
 *
 * @author hanbd
 */
public interface DocumentRepository extends ListCrudRepository<Document, UUID> {

    /**
     * 分页列出当前用户有权文档。
     *
     * @param userId 用户标识
     * @param q 名称或作者关键字，空串表示不筛选
     * @param limit 每页条数
     * @param offset 偏移
     * @return 文档列表
     */
    @Query(
            """
            SELECT d.id, d.title, d.authors, d.material_type, d.created_by, d.created_at, d.updated_at
            FROM document d
            JOIN document_acl a ON a.document_id = d.id
            WHERE a.user_id = :userId
              AND (
                :q = ''
                OR d.title ILIKE ('%' || :q || '%') ESCAPE '\\'
                OR COALESCE(d.authors, '') ILIKE ('%' || :q || '%') ESCAPE '\\'
              )
            ORDER BY d.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<Document> findAccessible(UUID userId, String q, int limit, int offset);

    /**
     * 统计当前用户有权文档数。
     *
     * @param userId 用户标识
     * @param q 名称或作者关键字，空串表示不筛选
     * @return 总数
     */
    @Query(
            """
            SELECT COUNT(*)
            FROM document d
            JOIN document_acl a ON a.document_id = d.id
            WHERE a.user_id = :userId
              AND (
                :q = ''
                OR d.title ILIKE ('%' || :q || '%') ESCAPE '\\'
                OR COALESCE(d.authors, '') ILIKE ('%' || :q || '%') ESCAPE '\\'
              )
            """)
    long countAccessible(UUID userId, String q);

    /**
     * 读取有权文档。
     *
     * @param documentId 文档标识
     * @param userId 用户标识
     * @return 文档
     */
    @Query(
            """
            SELECT d.id, d.title, d.authors, d.material_type, d.created_by, d.created_at, d.updated_at
            FROM document d
            JOIN document_acl a ON a.document_id = d.id
            WHERE d.id = :documentId AND a.user_id = :userId
            """)
    Optional<Document> findAccessible(UUID documentId, UUID userId);

    /**
     * 读取用户在文档上的角色。
     *
     * @param documentId 文档标识
     * @param userId 用户标识
     * @return 角色
     */
    @Query("SELECT role FROM document_acl WHERE document_id = :documentId AND user_id = :userId")
    Optional<String> findRole(UUID documentId, UUID userId);

    /**
     * 写入文档 ACL。
     *
     * @param documentId 文档标识
     * @param userId 用户标识
     * @param role 角色
     */
    @Modifying
    @Query("INSERT INTO document_acl (document_id, user_id, role) VALUES (:documentId, :userId, :role)")
    void insertAcl(UUID documentId, UUID userId, String role);

    /**
     * 判断用户是否可以查看。
     *
     * @param documentId 文档标识
     * @param userId 用户标识
     * @return 可查看返回 true
     */
    default boolean canView(UUID documentId, UUID userId) {
        return findRole(documentId, userId).isPresent();
    }

    /**
     * 判断用户是否至少拥有编辑权限。
     *
     * @param documentId 文档标识
     * @param userId 用户标识
     * @return 可编辑返回 true
     */
    default boolean canEdit(UUID documentId, UUID userId) {
        return findRole(documentId, userId).filter(role -> "editor".equals(role) || "owner".equals(role)).isPresent();
    }
}
