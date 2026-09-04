package com.ontotrace.semantic.persistence;

import com.ontotrace.semantic.EduArgument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * EDU 参数仓储。
 *
 * @author hanbd
 */
public interface EduArgumentRepository extends ListCrudRepository<EduArgument, UUID> {

    /**
     * 按 EDU 读取参数。
     *
     * @param eduId EDU 标识
     * @return 参数列表
     */
    List<EduArgument> findByEduId(UUID eduId);

    /**
     * 按多条 EDU 读取参数。
     *
     * @param eduIds EDU 标识
     * @return 参数列表
     */
    List<EduArgument> findByEduIdIn(List<UUID> eduIds);

    /**
     * 删除固定版本下全部参数。
     *
     * @param documentVersionId 版本标识
     */
    @Modifying
    @Query(
            """
            DELETE FROM edu_argument
            WHERE edu_id IN (SELECT id FROM edu WHERE document_version_id = :documentVersionId)
            """)
    void deleteByDocumentVersionId(UUID documentVersionId);

    /**
     * 删除指定 EDU 的参数。
     *
     * @param eduIds EDU 标识
     */
    @Modifying
    @Query("DELETE FROM edu_argument WHERE edu_id IN (:eduIds)")
    void deleteByEduIdIn(List<UUID> eduIds);
}
