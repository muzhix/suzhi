package com.ontotrace.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 文本单元仓储。
 *
 * @author hanbd
 */
public interface TextUnitRepository extends ListCrudRepository<TextUnit, UUID> {

    /**
     * 按顺序读取固定版本文本单元。
     *
     * @param documentVersionId 版本标识
     * @return 文本单元
     */
    List<TextUnit> findByDocumentVersionIdOrderBySeqAsc(UUID documentVersionId);

    /**
     * 只读 path，供目录树聚合，避免把全书正文载入内存。
     *
     * @param documentVersionId 版本标识
     * @return path 列表
     */
    @Query("SELECT path FROM text_unit WHERE document_version_id = :documentVersionId ORDER BY seq")
    List<String> findPathsByDocumentVersionId(UUID documentVersionId);

    /**
     * 按 path 前缀读取文本单元。
     *
     * @param documentVersionId 版本标识
     * @param prefix 精确 path
     * @param prefixLike 转义后的 {@code prefix/%} LIKE 模式
     * @return 文本单元
     */
    @Query(
            """
            SELECT id, document_version_id, seq, path, display_text, page_no
            FROM text_unit
            WHERE document_version_id = :documentVersionId
              AND (path = :prefix OR path LIKE :prefixLike ESCAPE '\\')
            ORDER BY seq
            """)
    List<TextUnit> findByDocumentVersionIdAndPathPrefix(UUID documentVersionId, String prefix, String prefixLike);
}
