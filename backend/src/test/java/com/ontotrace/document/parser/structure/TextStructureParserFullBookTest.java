package com.ontotrace.document.parser.structure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 若本机有 {@code doc/正史/} 全书，用预置方案跑一遍解析；没有则跳过，不把全书拷进仓库。
 *
 * @author hanbd
 */
class TextStructureParserFullBookTest {

    private static final Logger log = LoggerFactory.getLogger(TextStructureParserFullBookTest.class);
    private final StructureSchemeRegistry registry = new StructureSchemeRegistry();
    private final TextStructureParser parser = new TextStructureParser();

    /**
     * 三套体例全书（若存在）应能识别标题且不退化成 pN。
     *
     * @throws Exception 读文件失败
     */
    @Test
    void fullBooksIfPresent() throws Exception {
        int ran = 0;
        ran += check("jizhuan-toc-divergent");
        ran += check("jizhuan-toc-same");
        ran += check("biannian-juan-ji-nian");
        if (ran == 0) {
            log.warn("skip full-book parser: doc/正史/ 缺失或没有对应全书");
        }
    }

    private int check(String schemeId) throws Exception {
        Optional<Path> book = ZhengshiCorpus.find(schemeId);
        if (book.isEmpty()) {
            return 0;
        }
        String text = Files.readString(book.get(), StandardCharsets.UTF_8);
        TextStructureParser.ParseResult result = parser.parse(text, registry.require(schemeId));
        log.info(
                "full-book scheme={} file={} headings={} units={} acceptable={}",
                schemeId,
                book.get().getFileName(),
                result.headingCount(),
                result.units().size(),
                result.acceptable());
        if (result.units().stream().anyMatch(unit -> unit.path().matches("p\\d+"))) {
            throw new AssertionError(schemeId + " 退化成了 pN");
        }
        if (result.headingCount() == 0) {
            throw new AssertionError(schemeId + " 没有识别到标题: " + book.get());
        }
        if ("jizhuan-toc-divergent".equals(schemeId)) {
            boolean juanEr = result.units().stream()
                    .anyMatch(unit -> "卷二 本纪第二 太宗上".equals(unit.path()));
            if (!juanEr) {
                throw new AssertionError(schemeId + " 未对照 卷二 本纪第二 太宗上: " + result.summary());
            }
            if (result.units().stream().anyMatch(unit -> unit.path().contains("/"))) {
                throw new AssertionError(schemeId + " path 含多层 /");
            }
            if (result.units().stream().anyMatch(unit -> "文前".equals(unit.path()))) {
                throw new AssertionError(schemeId + " 目录树出现文前");
            }
            if (!result.unmatchedVolumes().isEmpty()) {
                throw new AssertionError(schemeId + " 未对照卷 " + result.unmatchedVolumes());
            }
            for (String path : List.of(
                    "卷九十五 列传四十五 睿宗诸子",
                    "卷九十六 列伟第四十六",
                    "卷一百三十四 列传八十四",
                    "卷一百六十 列传卷第一百一十")) {
                if (result.units().stream().noneMatch(unit -> path.equals(unit.path()))) {
                    throw new AssertionError(schemeId + " 未对照 " + path + ": " + result.summary());
                }
            }
        }
        if ("jizhuan-toc-same".equals(schemeId)) {
            if (result.units().stream().anyMatch(unit -> unit.path().contains("/"))) {
                throw new AssertionError(schemeId + " path 含多层 /");
            }
            if (result.units().stream().anyMatch(unit -> "文前".equals(unit.path()))) {
                throw new AssertionError(schemeId + " 目录树出现文前");
            }
            if (result.units().stream().anyMatch(unit -> unit.path().equals(unit.text()))) {
                throw new AssertionError(schemeId + " 卷标题进了 unit");
            }
            long maxPerPath = result.units().stream()
                    .collect(Collectors.groupingBy(TextStructureParser.Unit::path, Collectors.counting()))
                    .values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);
            if (maxPerPath <= 1) {
                throw new AssertionError(schemeId + " 整卷合成一段 " + result.summary());
            }
            if (result.outline().stream().anyMatch(node -> !node.children().isEmpty())) {
                throw new AssertionError(schemeId + " 目录同形不应拆成两级");
            }
        }
        return 1;
    }
}
