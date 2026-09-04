package com.ontotrace.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 项目仓储。
 *
 * @author hanbd
 */
public interface ProjectRepository extends ListCrudRepository<Project, UUID> {

    /**
     * 读取用户作为成员的项目。
     *
     * @param projectId 项目标识
     * @param userId 用户标识
     * @return 项目
     */
    @Query(
            """
            SELECT p.id, p.name, p.description, p.created_by, p.created_at, p.updated_at
            FROM project p
            JOIN project_member m ON m.project_id = p.id
            WHERE p.id = :projectId AND m.user_id = :userId
            """)
    Optional<Project> findAccessible(UUID projectId, UUID userId);

    /**
     * 当前用户同时拥有文档 ACL 的项目文档。
     *
     * @param projectId 项目标识
     * @param userId 用户标识
     * @return 文档标识
     */
    @Query(
            """
            SELECT pd.document_id
            FROM project_document pd
            JOIN document_acl a ON a.document_id = pd.document_id AND a.user_id = :userId
            WHERE pd.project_id = :projectId
            """)
    List<UUID> findVisibleDocumentIds(UUID projectId, UUID userId);

    /**
     * 是否为项目成员。
     *
     * @param projectId 项目标识
     * @param userId 用户标识
     * @return 成员返回 true
     */
    @Query("SELECT COUNT(*) > 0 FROM project_member WHERE project_id = :projectId AND user_id = :userId")
    boolean existsMember(UUID projectId, UUID userId);

    /**
     * 写入项目成员。
     *
     * @param projectId 项目标识
     * @param userId 用户标识
     * @param role 角色
     */
    @Modifying
    @Query("INSERT INTO project_member (project_id, user_id, role) VALUES (:projectId, :userId, :role)")
    void insertMember(UUID projectId, UUID userId, String role);

    /**
     * 绑定或更新项目文档版本。
     *
     * @param projectId 项目标识
     * @param documentId 文档标识
     * @param documentVersionId 版本标识
     */
    @Modifying
    @Query(
            """
            INSERT INTO project_document (project_id, document_id, document_version_id)
            VALUES (:projectId, :documentId, :documentVersionId)
            ON CONFLICT (project_id, document_id) DO UPDATE SET document_version_id = EXCLUDED.document_version_id
            """)
    void upsertDocument(UUID projectId, UUID documentId, UUID documentVersionId);
}
