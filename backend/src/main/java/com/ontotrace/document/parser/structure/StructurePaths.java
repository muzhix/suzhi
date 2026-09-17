package com.ontotrace.document.parser.structure;

/**
 * path 前缀与默认跳过 EDU 的判定。
 *
 * @author hanbd
 */
public final class StructurePaths {

    private StructurePaths() {}

    /**
     * 单元是否落在前缀下（等于前缀或为其子孙）。
     *
     * @param path 单元 path
     * @param prefix 前缀
     * @return 命中返回 true
     */
    public static boolean underPrefix(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /**
     * 文前目录、附录、表等节点默认不抽 EDU。
     *
     * @param path 结构路径
     * @return 应跳过返回 true
     */
    public static boolean skipEdu(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String first = path.split("/", 2)[0];
        if ("目录".equals(first) || "附录".equals(first) || "文前".equals(first) || "表".equals(first)) {
            return true;
        }
        for (String part : path.split("/")) {
            if (part.contains("世表") || part.contains("年表") || part.contains("月表")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 转义 LIKE 通配符，使前缀按字面匹配。
     *
     * @param prefix 前缀
     * @return 转义后的模式前缀
     */
    public static String likeLiteral(String prefix) {
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
