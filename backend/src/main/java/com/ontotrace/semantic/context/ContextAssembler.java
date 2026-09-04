package com.ontotrace.semantic.context;

import com.ontotrace.document.TextUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 按文本单元组装带稳定 contextKey 的提示上下文。
 *
 * @author hanbd
 */
@Component
public class ContextAssembler {

    /**
     * 一层上下文块。
     *
     * @param key 传输用 contextKey
     * @param purpose primary-source 或 context-only
     * @param text 文本
     * @param textUnitId 文本单元标识
     */
    public record Block(String key, String purpose, String text, UUID textUnitId) {}

    /**
     * 组装结果。
     *
     * @param prompt 模型输入
     * @param blocks 块列表
     * @param targetText 分析目标
     */
    public record Assembled(String prompt, List<Block> blocks, String targetText) {}

    /**
     * 为指定文本单元组装上下文。目标文本不会被裁剪。
     *
     * @param units 顺序文本单元
     * @param targetIndex 目标下标
     * @return 组装结果
     */
    public Assembled assemble(List<TextUnit> units, int targetIndex) {
        TextUnit target = units.get(targetIndex);
        List<Block> blocks = new ArrayList<>();
        StringBuilder prompt = new StringBuilder();
        if (targetIndex > 0) {
            TextUnit prev = units.get(targetIndex - 1);
            Block neighbor = new Block("previous-1", "context-only", prev.getDisplayText(), prev.getId());
            blocks.add(neighbor);
            prompt.append("[NEIGHBOR key=previous-1 purpose=context-only]\n")
                    .append(prev.getDisplayText())
                    .append("\n\n");
        }
        Block targetBlock = new Block("target", "primary-source", target.getDisplayText(), target.getId());
        blocks.add(targetBlock);
        prompt.append("[TARGET key=target purpose=primary-source]\n").append(target.getDisplayText());
        return new Assembled(prompt.toString(), List.copyOf(blocks), target.getDisplayText());
    }
}
