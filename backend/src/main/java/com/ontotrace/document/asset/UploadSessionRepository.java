package com.ontotrace.document.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 上传会话仓储。
 *
 * @author hanbd
 */
public interface UploadSessionRepository extends ListCrudRepository<UploadSession, UUID> {

    /**
     * 文档最近一次完成上传。
     *
     * @param documentId 文档标识
     * @param status 状态
     * @return 会话
     */
    Optional<UploadSession> findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(UUID documentId, String status);
}
