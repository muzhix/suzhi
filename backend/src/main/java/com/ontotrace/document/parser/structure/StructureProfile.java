package com.ontotrace.document.parser.structure;

import java.util.List;

/**
 * 一书一份结构方案：文前目录、标题层级、对齐方式、切段规则与 EDU 跳过节点。
 * 预置按体例命名，不绑书名。
 *
 * @param id 方案标识
 * @param name 展示名
 * @param toc 文前目录规则
 * @param headings 标题规则，从上到下先匹配者生效
 * @param alignment {@code lookup_volume_from_toc} 或 {@code title_has_volume}
 * @param paragraph {@code blank}、{@code indent}、{@code blank_or_indent} 或 {@code line}（一行一段）
 * @param neighbor 邻居边界，B1 写入快照，组装仍归 B2
 * @param skipEduNodeTypes 默认不抽 EDU 的节点类型
 * @author hanbd
 */
public record StructureProfile(
        String id,
        String name,
        TocRule toc,
        List<HeadingRule> headings,
        String alignment,
        String paragraph,
        NeighborRule neighbor,
        List<String> skipEduNodeTypes) {

    /**
     * 文前目录规则。
     *
     * @param present 是否有目录
     * @param linePattern 目录行正则，分组 1 为卷号、分组 2 为反查键
     * @param usage {@code volume_lookup} 或 {@code ignore}
     * @param dropLeadingCopy 丢掉与正文同形的文前目录副本
     */
    public record TocRule(Boolean present, String linePattern, String usage, Boolean dropLeadingCopy) {
        /**
         * 是否识别文前目录。
         *
         * @return 有目录返回 true
         */
        public boolean enabled() {
            return Boolean.TRUE.equals(present);
        }

        /**
         * 是否丢掉文前副本。
         *
         * @return 需要丢掉返回 true
         */
        public boolean dropCopy() {
            return Boolean.TRUE.equals(dropLeadingCopy);
        }

        /**
         * 是否用目录反查卷号。
         *
         * @return 反查返回 true
         */
        public boolean lookupVolume() {
            return "volume_lookup".equals(usage);
        }
    }

    /**
     * 一条标题规则。
     *
     * @param id 规则标识，如 juan、pian、ji、nian、wang
     * @param pattern 整行正则
     * @param level 栈深度，有 pathTemplate 时忽略
     * @param label 节点标签模板，如 {@code $1}
     * @param pathTemplate 由本行一次生成整条 path 的模板；空则按 level 压栈。多项用 {@code /} 连接；目录异形、目录同形用一项拼成一层字符串
     * @param tocKeyTemplate 反查目录用的键，如 {@code $1$2}；填入模板的 {@code {tocKey}}
     * @param nodeType 节点类型，如 pian、table、nian
     * @param mergeNextYear 王名行并入随后的元年标题
     */
    public record HeadingRule(
            String id,
            String pattern,
            Integer level,
            String label,
            List<String> pathTemplate,
            String tocKeyTemplate,
            String nodeType,
            Boolean mergeNextYear) {
        /**
         * 栈深度，缺省为 1。
         *
         * @return 深度
         */
        public int stackLevel() {
            return level == null ? 1 : level;
        }

        /**
         * 是否把下一行年号接到本王名上。
         *
         * @return 需要合并返回 true
         */
        public boolean mergeYear() {
            return Boolean.TRUE.equals(mergeNextYear);
        }

        /**
         * 是否用模板整行生成 path。
         *
         * @return 有模板返回 true
         */
        public boolean replacesPath() {
            return pathTemplate != null && !pathTemplate.isEmpty();
        }
    }

    /**
     * 邻居不跨卷 / 编年体默认可不跨年。B1 只快照，不组装上下文。
     *
     * @param noCrossJuan 不跨卷
     * @param noCrossNian 不跨年
     */
    public record NeighborRule(Boolean noCrossJuan, Boolean noCrossNian) {}

    /**
     * 标题规则列表，空则无规则。
     *
     * @return 规则
     */
    public List<HeadingRule> headingRules() {
        return headings == null ? List.of() : headings;
    }

    /**
     * 跳过 EDU 的节点类型。
     *
     * @return 类型列表
     */
    public List<String> skipTypes() {
        return skipEduNodeTypes == null ? List.of() : skipEduNodeTypes;
    }
}
