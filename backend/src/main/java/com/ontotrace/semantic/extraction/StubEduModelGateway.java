package com.ontotrace.semantic.extraction;

import java.util.List;

/**
 * 无密钥或测试用的确定性 EDU 生成器。摘录取自目标原文，保证可定位。
 *
 * @author hanbd
 */
public class StubEduModelGateway implements EduModelGateway {

    /**
     * 从目标句生成一条 EVENT。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    @Override
    public EduGenerationResult generate(EduGenerationRequest request) {
        String target = request.targetText() == null ? "" : request.targetText().trim();
        String quote = target.length() > 40 ? target.substring(0, 40) : target;
        EduValidator.ModelEdu edu = new EduValidator.ModelEdu(
                "EVENT",
                quote,
                "记述",
                List.of(new EduValidator.Argument("subject", null, "文本所述主体", quote, "PERSON")),
                List.of(new EduValidator.Source("target", quote)),
                false,
                null);
        return new EduGenerationResult(List.of(edu), "stub", "edu-generate-v1", 0, 0, 1);
    }

    /**
     * 复核一律通过，词元与延迟为零。
     *
     * @param request 复核请求
     * @return 通过结果
     */
    @Override
    public EduReviewResult review(EduReviewRequest request) {
        return new EduReviewResult(true, List.of(), "stub pass", "stub", 0, 0, 0);
    }
}
