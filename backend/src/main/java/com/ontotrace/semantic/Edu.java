package com.ontotrace.semantic;

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
 * 已持久化的 EDU。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("edu")
public class Edu implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID documentVersionId;
    private String type;
    private String text;
    private String predicate;
    private String timeValue;
    private String timeSourceForm;
    private String timePrecision;
    private String status;
    private Long revision;
    private String locationPrecision;
    private Boolean usedExternalKnowledge;
    private String reviewResult;
    private String reviewNotes;
    private UUID processingRunId;
    private UUID supersedesEduId;
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
