package com.ontotrace.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ontotrace.document.TextUnit;
import com.ontotrace.semantic.context.ContextAssembler;
import com.ontotrace.semantic.extraction.EduValidator;
import com.ontotrace.semantic.extraction.SourceLocator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * EDU 契约离线回归：定位、角色、禁止推断。
 *
 * @author hanbd
 */
class EduContractTest {

    private final SourceLocator locator = new SourceLocator();
    private final EduValidator validator = new EduValidator();
    private final ContextAssembler assembler = new ContextAssembler();

    /**
     * 摘录能在目标块中命中时为精确。
     */
    @Test
    void exactLocation() {
        ContextAssembler.Assembled assembled = context("六月四日，太宗率长孙无忌于玄武门诛之。");
        SourceLocator.Location location = locator.locate(assembled, "target", "于玄武门诛之");
        assertEquals(SourceLocator.Precision.exact, location.precision());
    }

    /**
     * 摘录不在原文中时降级到文本单元。
     */
    @Test
    void unitLocation() {
        ContextAssembler.Assembled assembled = context("六月四日，太宗率长孙无忌于玄武门诛之。");
        SourceLocator.Location location = locator.locate(assembled, "target", "李世民在玄武门发动兵变");
        assertEquals(SourceLocator.Precision.unit, location.precision());
    }

    /**
     * contextKey 不存在时定位失败。
     */
    @Test
    void failedLocation() {
        ContextAssembler.Assembled assembled = context("六月四日，太宗率长孙无忌于玄武门诛之。");
        SourceLocator.Location location = locator.locate(assembled, "missing", "于玄武门诛之");
        assertEquals(SourceLocator.Precision.failed, location.precision());
    }

    /**
     * 固定角色合法，other 必须带 roleName。
     */
    @Test
    void roleChecks() {
        assertTrue(validator.validRole("subject", null));
        assertFalse(validator.validRole("other", null));
        assertTrue(validator.validRole("other", "告密者"));
        assertFalse(validator.validRole("hero", null));
    }

    /**
     * 禁止推断样本不能通过校验。
     */
    @Test
    void forbidInference() {
        EduValidator.ModelEdu forbidden = new EduValidator.ModelEdu(
                "EVENT",
                "李世民因觊觎皇位而发动玄武门之变。",
                "谋害",
                List.of(new EduValidator.Argument("subject", null, "李世民", "世民", "PERSON")),
                List.of(new EduValidator.Source("target", "世民")),
                false,
                null);
        assertFalse(validator.validate(forbidden, false).isEmpty());
    }

    private ContextAssembler.Assembled context(String text) {
        TextUnit unit = TextUnit.builder()
                .id(UUID.randomUUID())
                .documentVersionId(UUID.randomUUID())
                .seq(1)
                .path("p1")
                .displayText(text)
                .build();
        return assembler.assemble(List.of(unit), 0);
    }
}
