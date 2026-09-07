package com.ontotrace.runcontrol;

import java.math.BigDecimal;
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
 * 处理运行：记录一次生成或解析运行的输入、模型、提示版本、词元、费用和结果。
 * jsonb 列（parameters、review_result_summary）由仓储的 CAST 语句单独写入。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("processing_run")
public class ProcessingRun implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID jobId;
    private UUID inputDocumentVersionId;
    private String inputRange;
    private String contextStrategy;
    private String provider;
    private String modelId;
    private String promptVersion;
    private String outputSchemaVersion;
    private Integer tokenInput;
    private Integer tokenOutput;
    private BigDecimal costUsd;
    private Integer latencyMs;
    private String status;
    private String reviewModelId;
    private String reviewPromptVersion;
    private String attachmentObjectKey;
    private Instant createdAt;
    private Instant finishedAt;
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
