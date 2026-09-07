package com.ontotrace.semantic.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * EDU JSON 映射离线检查。
 *
 * @author hanbd
 */
class EduJsonMapperTest {

    /**
     * 符合 schema 的 JSON 能映射并通过校验。
     */
    @Test
    void mapsValidGeneration() {
        String json =
                """
                {
                  "edus": [
                    {
                      "type": "EVENT",
                      "text": "李世民率长孙无忌于玄武门诛建成。",
                      "predicate": "诛",
                      "arguments": [
                        {"role": "subject", "value": "李世民", "sourceForm": "太宗", "entityType": "PERSON"}
                      ],
                      "time": {"value": "626-07", "sourceForm": "六月四日", "precision": "DAY"},
                      "sources": [{"contextKey": "target", "quote": "太宗率长孙无忌于玄武门诛之"}],
                      "usedExternalKnowledge": false
                    }
                  ]
                }
                """;
        List<EduValidator.ModelEdu> edus = EduJsonMapper.parseGeneration(json);
        assertEquals(1, edus.size());
        EduValidator.ModelEdu edu = edus.getFirst();
        assertEquals("EVENT", edu.type());
        assertEquals("DAY", edu.time().precision());
        assertTrue(new EduValidator().validate(edu, false).isEmpty());
    }

    /**
     * Markdown 围栏中的 JSON 也能解析。
     */
    @Test
    void stripsFence() {
        String json =
                """
                ```json
                {"edus": []}
                ```
                """;
        assertTrue(EduJsonMapper.parseGeneration(json).isEmpty());
    }

    /**
     * 复核 JSON 映射 passed 与 flags。
     */
    @Test
    void mapsReview() {
        EduModelGateway.EduReviewResult result = EduJsonMapper.parseReview(
                "{\"passed\":false,\"flags\":[\"忠实性\"],\"note\":\"quote 不在原文\"}", "qwen", 120, 8, 900);
        assertFalse(result.passed());
        assertEquals(List.of("忠实性"), result.flags());
        assertEquals("qwen", result.modelId());
        assertEquals(120, result.tokenInput());
        assertEquals(8, result.tokenOutput());
        assertEquals(900, result.latencyMs());
    }
}
