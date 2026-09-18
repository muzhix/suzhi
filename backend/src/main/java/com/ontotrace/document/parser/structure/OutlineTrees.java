package com.ontotrace.document.parser.structure;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由 path 聚合目录树，不建篇章表。公元、干支是节点附注，不写入 path。
 *
 * @author hanbd
 */
public final class OutlineTrees {

    private OutlineTrees() {}

    /**
     * 一条 path 及其可选公元、干支。同一 path 多次出现时，先出现的非空附注保留。
     *
     * @param path 结构路径
     * @param ceYear 公元纪年，阿拉伯数字，没有则为 null
     * @param ganzhi 干支，没有则为 null
     */
    public record PathNote(String path, Integer ceYear, String ganzhi) {}

    /**
     * 目录节点。
     *
     * @param path 从根到本节点的路径
     * @param label 本层标签
     * @param unitCount 本节点及子孙的文本单元数
     * @param children 子节点
     * @param ceYear 公元纪年，仅年节点可能有
     * @param ganzhi 干支，仅年节点可能有
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OutlineNode(
            String path, String label, int unitCount, List<OutlineNode> children, Integer ceYear, String ganzhi) {
        /**
         * 无附注的节点。
         *
         * @param path 路径
         * @param label 标签
         * @param unitCount 单元数
         * @param children 子节点
         */
        public OutlineNode(String path, String label, int unitCount, List<OutlineNode> children) {
            this(path, label, unitCount, children, null, null);
        }
    }

    /**
     * 按出现顺序聚合 path。
     *
     * @param paths 文本单元 path
     * @return 根层节点
     */
    public static List<OutlineNode> fromPaths(Iterable<String> paths) {
        List<PathNote> notes = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                notes.add(new PathNote(path, null, null));
            }
        }
        return fromPathNotes(notes);
    }

    /**
     * 按出现顺序聚合 path，并把公元、干支挂到对应叶子。
     *
     * @param notes path 与附注
     * @return 根层节点
     */
    public static List<OutlineNode> fromPathNotes(Iterable<PathNote> notes) {
        Node root = new Node("", "");
        if (notes == null) {
            return List.of();
        }
        for (PathNote note : notes) {
            if (note == null || note.path() == null || note.path().isBlank()) {
                continue;
            }
            String[] parts = note.path().split("/");
            Node current = root;
            String acc = "";
            for (String part : parts) {
                acc = acc.isEmpty() ? part : acc + "/" + part;
                current = current.child(part, acc);
            }
            current.note(note.ceYear(), note.ganzhi());
            Node cursor = current;
            while (cursor != null) {
                cursor.unitCount++;
                cursor = cursor.parent;
            }
        }
        return root.toChildren();
    }

    private static final class Node {
        private final String path;
        private final String label;
        private final Node parent;
        private final Map<String, Node> children = new LinkedHashMap<>();
        private int unitCount;
        private Integer ceYear;
        private String ganzhi;

        private Node(String path, String label) {
            this(path, label, null);
        }

        private Node(String path, String label, Node parent) {
            this.path = path;
            this.label = label;
            this.parent = parent;
        }

        private Node child(String label, String path) {
            return children.computeIfAbsent(label, key -> new Node(path, key, this));
        }

        private void note(Integer ceYear, String ganzhi) {
            if (this.ceYear == null && ceYear != null) {
                this.ceYear = ceYear;
            }
            if (this.ganzhi == null && ganzhi != null && !ganzhi.isBlank()) {
                this.ganzhi = ganzhi;
            }
        }

        private List<OutlineNode> toChildren() {
            List<OutlineNode> nodes = new ArrayList<>();
            for (Node child : children.values()) {
                nodes.add(new OutlineNode(
                        child.path, child.label, child.unitCount, child.toChildren(), child.ceYear, child.ganzhi));
            }
            return nodes;
        }
    }
}
