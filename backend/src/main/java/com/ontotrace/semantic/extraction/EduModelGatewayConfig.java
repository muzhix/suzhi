package com.ontotrace.semantic.extraction;

import com.ontotrace.config.OntoTraceProperties;
import com.ontotrace.web.ApiExceptionHandler.UnprocessableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 按 ontotrace.ai.mode 选择模型网关。
 *
 * @author hanbd
 */
@Configuration
public class EduModelGatewayConfig {

    /**
     * 选择 stub、live 或 disabled。
     *
     * @param properties 运行参数
     * @param environment 环境，用于读取 API Key
     * @param chatModels 可选 ChatModel
     * @return 网关
     */
    @Bean
    public EduModelGateway eduModelGateway(
            OntoTraceProperties properties, Environment environment, ObjectProvider<ChatModel> chatModels) {
        String mode = properties.getAi().getMode();
        if ("stub".equals(mode)) {
            return new StubEduModelGateway();
        }
        if ("live".equals(mode)) {
            String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
            if (apiKey.isBlank() || "dummy".equals(apiKey)) {
                throw new IllegalStateException("ontotrace.ai.mode=live 需要有效的 AI_API_KEY");
            }
            ChatModel chatModel = chatModels.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("live 模式未创建 ChatModel，请检查 spring.ai.openai 配置");
            }
            return new SpringAiEduModelGateway(ChatClient.create(chatModel), properties);
        }
        return new DisabledEduModelGateway();
    }

    /**
     * 未配置模型时拒绝抽取。
     */
    static class DisabledEduModelGateway implements EduModelGateway {
        @Override
        public EduGenerationResult generate(EduGenerationRequest request) {
            throw new UnprocessableException("未配置模型。设置 AI_API_KEY 并将 ontotrace.ai.mode 设为 live，或本地使用 stub");
        }

        @Override
        public EduReviewResult review(EduReviewRequest request) {
            throw new UnprocessableException("未配置模型，无法复核");
        }
    }
}
