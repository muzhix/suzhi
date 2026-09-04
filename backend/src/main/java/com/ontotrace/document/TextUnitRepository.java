package com.ontotrace.document;

import java.util.List;
import java.util.UUID;
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
}
