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
 * 已校验的原始资产。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("asset")
public class Asset implements Persistable<UUID> {

    @Id
    private UUID id;
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String checksumSha256;
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
