package com.ontotrace.document.parser.structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在 {@code doc/正史/} 中查找全书 txt。目录 gitignore，缺失时跳过上传测试。
 *
 * @author hanbd
 */
public final class ZhengshiCorpus {

    private static final Logger log = LoggerFactory.getLogger(ZhengshiCorpus.class);

    private ZhengshiCorpus() {}

    /**
     * 按体例查找一本全书。
     *
     * @param schemeId 预置方案标识
     * @return 文件路径
     */
    public static Optional<Path> find(String schemeId) {
        Path dir = resolveDir();
        if (dir == null) {
            log.warn("skip full-book test: doc/正史/ 不存在");
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            Optional<Path> found = stream.filter(Files::isRegularFile)
                    .filter(path -> matches(schemeId, path.getFileName().toString()))
                    .findFirst();
            if (found.isEmpty()) {
                log.warn("skip full-book test: doc/正史/ 没有 {} 对应全书", schemeId);
            }
            return found;
        } catch (Exception ex) {
            log.warn("skip full-book test: 无法读取 doc/正史/ {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static Path resolveDir() {
        String userDir = System.getProperty("user.dir");
        for (Path candidate : new Path[] {
            Path.of(userDir, "doc", "正史"),
            Path.of(userDir, "..", "doc", "正史").normalize(),
            Path.of("/workspace/doc/正史")
        }) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean matches(String schemeId, String filename) {
        String n = filename.toLowerCase(Locale.ROOT);
        if (!n.endsWith(".txt") && !n.endsWith(".md")) {
            return false;
        }
        return switch (schemeId) {
            case "jizhuan-toc-divergent" -> contains(n, "旧唐书", "舊唐書");
            case "jizhuan-toc-same" -> contains(n, "史记", "史記") && !contains(n, "唐书", "唐書");
            case "biannian-juan-ji-nian" -> contains(n, "通鉴", "通鑑", "资治通鉴", "資治通鑑");
            default -> false;
        };
    }

    private static boolean contains(String filename, String... needles) {
        for (String needle : needles) {
            if (filename.contains(needle.toLowerCase(Locale.ROOT)) || filename.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
