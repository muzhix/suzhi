package com.ontotrace.document;

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
 * 研究项目。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("project")
public class Project implements Persistable<UUID> {

    @Id
    private UUID id;
    private String name;
    private String description;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
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
