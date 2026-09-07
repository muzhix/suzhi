package com.ontotrace.semantic.extraction;

import java.util.List;

/**
 * EDU 模型网关，不泄漏 Spring AI 类型。
 *
 * @author hanbd
 */
public interface EduModelGateway {

    /**
     * 生成 EDU。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    EduGenerationResult generate(EduGenerationRequest request);

    /**
     * 复核 EDU。
     *
     * @param request 复核请求
     * @return 复核结果
     */
    EduReviewResult review(EduReviewRequest request);

    /**
     * 生成请求。
     *
     * @param prompt 上下文提示
     * @param targetText 分析目标
     */
    record EduGenerationRequest(String prompt, String targetText) {}

    /**
     * 生成结果。
     *
     * @param edus EDU 列表
     * @param modelId 模型
     * @param promptVersion 提示版本
     * @param tokenInput 输入词元
     * @param tokenOutput 输出词元
     * @param latencyMs 延迟
     */
    record EduGenerationResult(
            List<EduValidator.ModelEdu> edus,
            String modelId,
            String promptVersion,
            int tokenInput,
            int tokenOutput,
            long latencyMs) {}

    /**
     * 复核请求。
     *
     * @param prompt 上下文
     * @param edu 待复核 EDU
     */
    record EduReviewRequest(String prompt, EduValidator.ModelEdu edu) {}

    /**
     * 复核结果。
     *
     * @param passed 是否通过
     * @param flags 疑点类别
     * @param note 说明
     * @param modelId 复核模型
     * @param tokenInput 输入词元
     * @param tokenOutput 输出词元
     * @param latencyMs 延迟
     */
    record EduReviewResult(
            boolean passed,
            List<String> flags,
            String note,
            String modelId,
            int tokenInput,
            int tokenOutput,
            long latencyMs) {}
}
