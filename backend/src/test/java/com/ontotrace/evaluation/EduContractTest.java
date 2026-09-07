package com.ontotrace.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ontotrace.document.TextUnit;
import com.ontotrace.semantic.context.ContextAssembler;
import com.ontotrace.semantic.extraction.EduValidator;
import com.ontotrace.semantic.extraction.SourceLocator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * EDU 契约离线回归：定位、角色与结构校验，用例由 evaluation/gold 样本驱动。
 * 禁止推断属于模型复核职责，确定性校验不承担语义判断。
 *
 * @author hanbd
 */
class EduContractTest {

    private static final Path GOLD = Path.of("..", "evaluation", "gold", "historical");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SourceLocator locator = new SourceLocator();
    private final EduValidator validator = new EduValidator();
    private final ContextAssembler assembler = new ContextAssembler();

    /**
     * 金标准定位样本三级路径全部命中预期精度。
     *
     * @throws Exception 样本读取失败
     */
    @Test
    void locatorSamples() throws Exception {
        for (JsonNode sample : readSamples(GOLD.resolve("source-locator.jsonl"))) {
            SourceLocator.Location location = locator.locate(
                    context(sample.get("target").asText()),
                    sample.get("contextKey").asText(),
                    sample.get("quote").asText());
            assertEquals(
                    SourceLocator.Precision.valueOf(sample.get("expect").asText()),
                    location.precision(),
                    sample.get("id").asText());
        }
    }

    /**
     * 降级定位不携带原文摘录，未命中的模型摘录不能标成原文。
     */
    @Test
    void degradedLocationKeepsNoQuote() {
        ContextAssembler.Assembled assembled = context("六月四日，太宗率长孙无忌于玄武门诛之。");
        SourceLocator.Location location = locator.locate(assembled, "target", "李世民在玄武门发动兵变");
        assertEquals(SourceLocator.Precision.unit, location.precision());
        assertNull(location.quote());
        assertNull(location.charStart());
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
     * 模型未返回来源时确定性校验失败；系统不补造摘录。
     */
    @Test
    void missingSourcesFail() {
        EduValidator.ModelEdu noSources = new EduValidator.ModelEdu(
                "EVENT",
                "李世民率长孙无忌于玄武门伏兵。",
                "伏兵",
                List.of(new EduValidator.Argument("subject", null, "李世民", "太宗", "PERSON")),
                List.of(),
                false,
                null);
        assertTrue(validator.validate(noSources, false).contains("缺少来源"));
    }

    /**
     * 类型与时间精度必须属于输出契约枚举。
     */
    @Test
    void enumChecks() {
        assertTrue(validator.validate(validEdu(), false).isEmpty());
        EduValidator.ModelEdu wrongType = new EduValidator.ModelEdu(
                "FACT", "text", "predicate",
                List.of(new EduValidator.Argument("subject", null, "李世民", "太宗", "PERSON")),
                List.of(new EduValidator.Source("target", "太宗")), false, null);
        EduValidator.ModelEdu badTime = new EduValidator.ModelEdu(
                "EVENT",
                "李世民率长孙无忌于玄武门伏兵。",
                "伏兵",
                List.of(new EduValidator.Argument("subject", null, "李世民", "太宗", "PERSON")),
                List.of(new EduValidator.Source("target", "太宗率长孙无忌")),
                false,
                new EduValidator.Time("武德九年", "九年", "CENTURY"));
        assertTrue(validator.validate(wrongType, false).stream().anyMatch(error -> error.contains("非法类型")));
        assertTrue(validator.validate(badTime, false).stream().anyMatch(error -> error.contains("非法时间精度")));
    }

    /**
     * 禁止推断样本只约束复核模型，不进入确定性词表：
     * 结构合法的推断性文本应通过确定性校验，由复核环节拦截。
     *
     * @throws Exception 样本读取失败
     */
    @Test
    void inferenceIsReviewScope() throws Exception {
        for (JsonNode sample : readSamples(GOLD.resolve("no-inference.jsonl"))) {
            EduValidator.ModelEdu edu = new EduValidator.ModelEdu(
                    "EVENT",
                    sample.get("text").asText(),
                    "谋害",
                    List.of(new EduValidator.Argument("subject", null, "李世民", "太宗", "PERSON")),
                    List.of(new EduValidator.Source("target", "世民")),
                    false,
                    null);
            assertEquals(
                    List.of(),
                    validator.validate(edu, false),
                    "确定性校验不应承担禁止推断判断: " + sample.get("id").asText());
        }
    }

    private EduValidator.ModelEdu validEdu() {
        return new EduValidator.ModelEdu(
                "EVENT",
                "李世民率长孙无忌于玄武门伏兵。",
                "伏兵",
                List.of(new EduValidator.Argument("subject", null, "李世民", "太宗", "PERSON")),
                List.of(new EduValidator.Source("target", "太宗率长孙无忌")),
                false,
                new EduValidator.Time("武德九年", "九年", "YEAR"));
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

    private static List<JsonNode> readSamples(Path file) throws Exception {
        List<JsonNode> samples = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                samples.add(JSON.readTree(line));
            }
        }
        return samples;
    }
}
