package com.ontotrace.semantic.extraction;

import com.ontotrace.semantic.context.ContextAssembler;
import java.util.UUID;

/**
 * 根据 contextKey 与摘录定位原文。
 *
 * @author hanbd
 */
public class SourceLocator {

    /**
     * 定位精度。
     */
    public enum Precision {
        exact,
        unit,
        page,
        failed
    }

    /**
     * 定位结果。
     *
     * @param precision 精度
     * @param textUnitId 文本单元
     * @param quote 摘录
     * @param charStart 起始偏移
     * @param charEnd 结束偏移
     */
    public record Location(Precision precision, UUID textUnitId, String quote, Integer charStart, Integer charEnd) {}

    /**
     * 定位摘录。
     *
     * @param assembled 组装上下文
     * @param contextKey 模型回显的键
     * @param quote 摘录
     * @return 定位结果，降级时 quote 为空
     */
    public Location locate(ContextAssembler.Assembled assembled, String contextKey, String quote) {
        ContextAssembler.Block block = assembled.blocks().stream()
                .filter(item -> item.key().equals(contextKey))
                .findFirst()
                .orElse(null);
        if (block == null) {
            return new Location(Precision.failed, null, quote, null, null);
        }
        if (quote == null || quote.isBlank()) {
            return new Location(Precision.unit, block.textUnitId(), null, null, null);
        }
        int start = block.text().indexOf(quote);
        if (start >= 0) {
            return new Location(Precision.exact, block.textUnitId(), quote, start, start + quote.length());
        }
        return new Location(Precision.unit, block.textUnitId(), null, null, null);
    }
}
