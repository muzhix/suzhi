package com.ontotrace.semantic;

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
 * EDU 来源引用。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("edu_source_ref")
public class EduSourceRef implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID eduId;
    private UUID documentVersionId;
    private UUID textUnitId;
    private String quote;
    private Integer charStart;
    private Integer charEnd;
    private String precision;
    private String purpose;
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
