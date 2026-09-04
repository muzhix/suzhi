package com.ontotrace.web;

/**
 * SQL LIKE 关键字转义。
 *
 * @author hanbd
 */
public final class LikeQuery {

    private LikeQuery() {}

    /**
     * 转义 LIKE 通配符。空白表示不筛选。
     *
     * @param raw 原始关键字
     * @return 可放入 ILIKE 的片段；空白则返回空串
     */
    public static String contains(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
