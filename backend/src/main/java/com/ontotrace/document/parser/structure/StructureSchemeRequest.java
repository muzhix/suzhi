package com.ontotrace.document.parser.structure;

/**
 * 提取预览或确认时提交的结构方案。profile 有标题规则时优先生效。
 *
 * @param schemeId 预置标识
 * @param profile 覆盖体
 * @author hanbd
 */
public record StructureSchemeRequest(String schemeId, StructureProfile profile) {}
