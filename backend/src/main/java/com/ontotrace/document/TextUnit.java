package com.ontotrace.document;

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
 * 固定版本中的文本单元。
 *
 * @author hanbd
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("text_unit")
public class TextUnit implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID documentVersionId;
    private Integer seq;
    private String path;
    private String displayText;
    private Integer pageNo;
    /** 公元纪年，阿拉伯数字；目录附注，不进 path。 */
    private Integer ceYear;
    /** 干支；目录附注，不进 path。 */
    private String ganzhi;
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
