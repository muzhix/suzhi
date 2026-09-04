package com.ontotrace.document.asset;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预签名上传会话。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("upload_session")
public class UploadSession implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID userId;
    private UUID documentId;
    private String objectKey;
    private String originalFilename;
    private String declaredContentType;
    private Long declaredSizeBytes;
    private String declaredChecksumSha256;
    private String status;
    private UUID assetId;
    private Instant expiresAt;
    private Instant createdAt;
    @Transient
    @Builder.Default
    private boolean isNew = true;

    /**
     * 是否尚未插入。
     *
     * @return 新记录返回 true
     */
    @Override
    public boolean isNew() {
        return isNew;
    }
}
