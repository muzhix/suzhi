package com.ontotrace.document.parser.structure;

/**
 * 内容提取任务携带的方案快照。
 *
 * @param schemeId 预置标识
 * @param profile 确认时的方案
 * @author hanbd
 */
public record ExtractJobPayload(String schemeId, StructureProfile profile) {}
