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
 * EDU 事件要素。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("edu_argument")
public class EduArgument implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID eduId;
    private String role;
    private String roleName;
    private String value;
    private String sourceForm;
    private String entityType;
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
