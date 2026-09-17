package com.ontotrace.document.parser.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由 path 聚合目录树，不建篇章表。
 *
 * @author hanbd
 */
public final class OutlineTrees {

    private OutlineTrees() {}

    /**
     * 目录节点。
     *
     * @param path 从根到本节点的路径
     * @param label 本层标签
     * @param unitCount 本节点及子孙的文本单元数
     * @param children 子节点
     */
    public record OutlineNode(String path, String label, int unitCount, List<OutlineNode> children) {}

    /**
     * 按出现顺序聚合 path。
     *
     * @param paths 文本单元 path
     * @return 根层节点
     */
    public static List<OutlineNode> fromPaths(Iterable<String> paths) {
        Node root = new Node("", "");
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String[] parts = path.split("/");
            Node current = root;
            String acc = "";
            for (String part : parts) {
                acc = acc.isEmpty() ? part : acc + "/" + part;
                current = current.child(part, acc);
            }
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

        private List<OutlineNode> toChildren() {
            List<OutlineNode> nodes = new ArrayList<>();
            for (Node child : children.values()) {
                nodes.add(new OutlineNode(child.path, child.label, child.unitCount, child.toChildren()));
            }
            return nodes;
        }
    }
}
