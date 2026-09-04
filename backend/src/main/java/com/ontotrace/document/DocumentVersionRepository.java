package com.ontotrace.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 文档版本仓储。
 *
 * @author hanbd
 */
public interface DocumentVersionRepository extends ListCrudRepository<DocumentVersion, UUID> {

    /**
     * 按版本号倒序列出。
     *
     * @param documentId 文档标识
     * @return 版本列表
     */
    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);

    /**
     * 最新版本。
     *
     * @param documentId 文档标识
     * @return 版本
     */
    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNoDesc(UUID documentId);
}
