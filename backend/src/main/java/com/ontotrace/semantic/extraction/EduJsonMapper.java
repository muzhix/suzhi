package com.ontotrace.semantic.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 将模型 JSON 映射为 {@link EduValidator.ModelEdu}，不访问网络。
 *
 * @author hanbd
 */
public final class EduJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EduJsonMapper() {}

    /**
     * 解析生成结果。
     *
     * @param json 模型 JSON
     * @return EDU 列表
     */
    public static List<EduValidator.ModelEdu> parseGeneration(String json) {
        try {
            GenerationPayload payload = MAPPER.readValue(stripFence(json), GenerationPayload.class);
            if (payload.edus() == null) {
                return List.of();
            }
            return payload.edus().stream().map(EduJson::toModel).toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析 EDU JSON: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将 EDU 序列化为复核输入。
     *
     * @param edu EDU
     * @return JSON
     */
    public static String toReviewInput(EduValidator.ModelEdu edu) {
        try {
            return MAPPER.writeValueAsString(edu);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法序列化 EDU", ex);
        }
    }

    /**
     * 解析复核结果。
     *
     * @param json 模型 JSON
     * @param modelId 复核模型
     * @return 复核
     */
    public static EduModelGateway.EduReviewResult parseReview(String json, String modelId) {
        try {
            ReviewPayload payload = MAPPER.readValue(stripFence(json), ReviewPayload.class);
            List<String> flags = payload.flags() == null ? List.of() : payload.flags();
            return new EduModelGateway.EduReviewResult(payload.passed(), flags, payload.note(), modelId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析复核 JSON: " + ex.getMessage(), ex);
        }
    }

    static String stripFence(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerationPayload(List<EduJson> edus) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReviewPayload(boolean passed, List<String> flags, String note) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EduJson(
            String type,
            String text,
            String predicate,
            List<ArgJson> arguments,
            List<SrcJson> sources,
            Boolean usedExternalKnowledge,
            TimeJson time) {
        EduValidator.ModelEdu toModel() {
            List<EduValidator.Argument> args = arguments == null
                    ? List.of()
                    : arguments.stream()
                            .map(a -> new EduValidator.Argument(a.role(), a.roleName(), a.value(), a.sourceForm(), a.entityType()))
                            .toList();
            List<EduValidator.Source> srcs = sources == null
                    ? List.of()
                    : sources.stream().map(s -> new EduValidator.Source(s.contextKey(), s.quote())).toList();
            EduValidator.Time timeValue = time == null
                    ? null
                    : new EduValidator.Time(time.value(), time.sourceForm(), time.precision());
            return new EduValidator.ModelEdu(
                    type, text, predicate, args, srcs, Boolean.TRUE.equals(usedExternalKnowledge), timeValue);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArgJson(String role, String roleName, String value, String sourceForm, String entityType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SrcJson(String contextKey, String quote) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeJson(String value, String sourceForm, String precision) {}
}
