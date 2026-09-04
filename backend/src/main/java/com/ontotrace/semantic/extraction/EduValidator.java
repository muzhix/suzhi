package com.ontotrace.semantic.extraction;

import java.util.List;
import java.util.Set;

/**
 * EDU 确定性校验。
 *
 * @author hanbd
 */
public class EduValidator {

    private static final Set<String> ROLES = Set.of(
            "subject", "object", "participant", "location", "origin", "destination", "instrument", "value", "other");
    private static final Set<String> FORBIDDEN_MARKERS = Set.of("觊觎皇位", "外部知识", "史书未载");

    /**
     * 模型参数。
     *
     * @param role 角色
     * @param roleName other 时的名称
     * @param value 规范值
     * @param sourceForm 原文写法
     * @param entityType 建议类型
     */
    public record Argument(String role, String roleName, String value, String sourceForm, String entityType) {}

    /**
     * 模型来源。
     *
     * @param contextKey 上下文键
     * @param quote 摘录
     */
    public record Source(String contextKey, String quote) {}

    /**
     * 模型 EDU。
     *
     * @param type 类型
     * @param text 自足表述
     * @param predicate 谓词
     * @param arguments 参数
     * @param sources 来源
     * @param usedExternalKnowledge 是否使用外部知识
     * @param time 可选时间
     */
    public record ModelEdu(
            String type,
            String text,
            String predicate,
            List<Argument> arguments,
            List<Source> sources,
            boolean usedExternalKnowledge,
            Time time) {}

    /**
     * 模型输出的时间。
     *
     * @param value 规范时间
     * @param sourceForm 原文写法
     * @param precision 精度
     */
    public record Time(String value, String sourceForm, String precision) {}

    /**
     * 校验一条 EDU。
     *
     * @param edu 模型输出
     * @param locationFailed 来源定位是否失败
     * @return 错误列表，空表示通过
     */
    public List<String> validate(ModelEdu edu, boolean locationFailed) {
        List<String> errors = new java.util.ArrayList<>();
        if (edu.type() == null || edu.type().isBlank()) {
            errors.add("缺少类型");
        }
        if (edu.text() == null || edu.text().isBlank()) {
            errors.add("缺少自足表述");
        }
        if (edu.predicate() == null || edu.predicate().isBlank()) {
            errors.add("缺少谓词");
        }
        if (edu.arguments() == null || edu.arguments().isEmpty()) {
            errors.add("缺少事件要素");
        } else {
            for (Argument argument : edu.arguments()) {
                if (!ROLES.contains(argument.role())) {
                    errors.add("非法角色: " + argument.role());
                }
                if ("other".equals(argument.role()) && (argument.roleName() == null || argument.roleName().isBlank())) {
                    errors.add("other 角色需要 roleName");
                }
                if (argument.value() == null || argument.value().isBlank()) {
                    errors.add("事件要素值为空");
                }
            }
        }
        if (edu.sources() == null || edu.sources().isEmpty()) {
            errors.add("缺少来源");
        }
        if (locationFailed) {
            errors.add("来源定位失败");
        }
        if (edu.text() != null) {
            for (String marker : FORBIDDEN_MARKERS) {
                if (edu.text().contains(marker)) {
                    errors.add("禁止推断: " + marker);
                }
            }
        }
        return errors;
    }

    /**
     * 角色是否合法。
     *
     * @param role 角色
     * @param roleName other 名称
     * @return 合法返回 true
     */
    public boolean validRole(String role, String roleName) {
        if (!ROLES.contains(role)) {
            return false;
        }
        return !"other".equals(role) || (roleName != null && !roleName.isBlank());
    }
}
