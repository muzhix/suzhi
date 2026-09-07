package com.ontotrace.semantic.extraction;

import com.ontotrace.config.OntoTraceProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 使用 Spring AI ChatClient 的 EDU 网关。不向领域层泄漏 Spring AI 类型。
 *
 * @author hanbd
 */
@Slf4j
public class SpringAiEduModelGateway implements EduModelGateway {

    private final ChatClient chatClient;
    private final OntoTraceProperties.Ai ai;
    private final String generateTemplate;
    private final String reviewTemplate;

    /**
     * 创建网关。
     *
     * @param chatClient 聊天客户端
     * @param properties 运行参数
     */
    public SpringAiEduModelGateway(ChatClient chatClient, OntoTraceProperties properties) {
        this.chatClient = chatClient;
        this.ai = properties.getAi();
        this.generateTemplate = loadPrompt("/prompts/edu-generate-v1.st");
        this.reviewTemplate = loadPrompt("/prompts/edu-review-v1.st");
        log.warn(
                "EDU live gateway uses prompt-constrained JSON; provider-native json_schema is not assumed for OpenAI-compatible endpoints");
    }

    @Override
    public EduGenerationResult generate(EduGenerationRequest request) {
        long started = System.nanoTime();
        String system = generateTemplate.replace("{context}", request.prompt());
        CallResult result = complete(system);
        List<EduValidator.ModelEdu> edus = EduJsonMapper.parseGeneration(result.content());
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        log.info(
                "edu generate mode=live model={} promptVersion={} eduCount={} latencyMs={} tokenIn={} tokenOut={}",
                ai.getChatModel(),
                ai.getGeneratePromptVersion(),
                edus.size(),
                latencyMs,
                result.inputTokens(),
                result.outputTokens());
        return new EduGenerationResult(
                edus,
                ai.getChatModel(),
                ai.getGeneratePromptVersion(),
                result.inputTokens(),
                result.outputTokens(),
                latencyMs);
    }

    @Override
    public EduReviewResult review(EduReviewRequest request) {
        long started = System.nanoTime();
        String eduJson = EduJsonMapper.toReviewInput(request.edu());
        String system = reviewTemplate.replace("{context}", request.prompt()).replace("{edu}", eduJson);
        CallResult result = complete(system);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        EduReviewResult parsed = EduJsonMapper.parseReview(
                result.content(), ai.getReviewModel(), result.inputTokens(), result.outputTokens(), latencyMs);
        log.info(
                "edu review mode=live model={} passed={} latencyMs={}",
                ai.getReviewModel(),
                parsed.passed(),
                latencyMs);
        return parsed;
    }

    private CallResult complete(String system) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(system)
                .user("请只输出 JSON，不要 Markdown 代码围栏。")
                .call()
                .chatResponse();
        String content = chatResponse == null || chatResponse.getResult() == null
                ? ""
                : chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
        int in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        return new CallResult(content == null ? "" : content, in, out);
    }

    private static String loadPrompt(String classpath) {
        try (InputStream in = SpringAiEduModelGateway.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("缺少提示词 " + classpath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取提示词 " + classpath, ex);
        }
    }

    private record CallResult(String content, int inputTokens, int outputTokens) {}
}
