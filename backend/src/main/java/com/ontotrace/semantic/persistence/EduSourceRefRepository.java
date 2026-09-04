package com.ontotrace.semantic.persistence;

import com.ontotrace.semantic.EduSourceRef;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * EDU 来源引用仓储。
 *
 * @author hanbd
 */
public interface EduSourceRefRepository extends ListCrudRepository<EduSourceRef, UUID> {

    /**
     * 按 EDU 读取来源。
     *
     * @param eduId EDU 标识
     * @return 来源列表
     */
    List<EduSourceRef> findByEduId(UUID eduId);

    /**
     * 按多条 EDU 读取来源。
     *
     * @param eduIds EDU 标识
     * @return 来源列表
     */
    List<EduSourceRef> findByEduIdIn(List<UUID> eduIds);

    /**
     * 按文本单元找到关联 EDU。
     *
     * @param textUnitId 文本单元标识
     * @return EDU 标识
     */
    @Query("SELECT DISTINCT edu_id FROM edu_source_ref WHERE text_unit_id = :textUnitId")
    List<UUID> findEduIdsByTextUnitId(UUID textUnitId);

    /**
     * 删除固定版本下来源。
     *
     * @param documentVersionId 版本标识
     */
    @Modifying
    @Query("DELETE FROM edu_source_ref WHERE document_version_id = :documentVersionId")
    void deleteByDocumentVersionId(UUID documentVersionId);

    /**
     * 删除指定 EDU 的来源。
     *
     * @param eduIds EDU 标识
     */
    @Modifying
    @Query("DELETE FROM edu_source_ref WHERE edu_id IN (:eduIds)")
    void deleteByEduIdIn(List<UUID> eduIds);
}
