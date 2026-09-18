package com.ontotrace.document.parser.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 三套体例切片：目录异形反查卷号一层 path、同形去文前目录且一层 path 按行切、编年卷/纪/年且年下多段。
 *
 * @author hanbd
 */
class TextStructureParserTest {

    private final StructureSchemeRegistry registry = new StructureSchemeRegistry();
    private final TextStructureParser parser = new TextStructureParser();

    /**
     * 正文标题反查目录卷号，拼成一层 path「卷号 目录篇名 正文篇题」；目录与标题行不进正文。
     */
    @Test
    void divergentLooksUpVolumeFromToc() {
        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        TextStructureParser.ParseResult result = parser.parse(read("jizhuan-toc-divergent.txt"), profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(3, result.headingCount());
        assertEquals(
                List.of(
                        "卷一 本纪第一 高祖",
                        "卷一 本纪第一 高祖",
                        "卷二 本纪第二 太宗上",
                        "卷二 本纪第二 太宗上",
                        "卷五十一 列传第一 后妃上"),
                paths(result));
        assertTrue(paths(result).stream().noneMatch(path -> path.contains("/")));
        assertEquals(
                List.of("卷一 本纪第一 高祖", "卷二 本纪第二 太宗上", "卷五十一 列传第一 后妃上"),
                result.outline().stream().map(OutlineTrees.OutlineNode::path).toList());
        assertTrue(result.outline().stream().allMatch(node -> node.children().isEmpty()));
        assertFalse(result.units().stream().anyMatch(unit -> unit.path().equals("目录") || unit.text().contains("卷一 本纪第一")));
        assertTrue(result.units().stream().noneMatch(unit -> unit.text().equals("本纪第一 高祖") || unit.text().equals("本纪第二 太宗上")
                || unit.text().equals("列传第一 后妃上")));
        assertEquals(2, count(result, "卷一 本纪第一 高祖"));
        assertTrue(result.unmatchedVolumes().isEmpty());
        assertTrue(result.units().stream().noneMatch(unit -> "文前".equals(unit.path())));
        assertTrue(result.outline().stream().noneMatch(node -> "文前".equals(node.path())));
        assertTrue(result.units().stream().noneMatch(unit -> unit.text().equals("附录")));
        assertEquals("纪传体·目录异形", profile.name());
        assertFalse(profile.name().contains("旧唐书"));
    }

    /**
     * 《旧唐书》正文标题行首两个 U+3000，须去掉后再匹配；「卷十七上」仍能从目录反查。
     */
    @Test
    void divergentIgnoresLeadingIdeographicSpaceOnBodyTitle() {
        String fixture = read("jizhuan-toc-divergent-leading-ws.txt");
        int titleAt = fixture.indexOf("本纪第二 太宗上");
        assertTrue(titleAt >= 2);
        assertEquals("\u3000\u3000", fixture.substring(titleAt - 2, titleAt));

        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        TextStructureParser.ParseResult result = parser.parse(fixture, profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(3, result.headingCount());
        assertEquals(
                List.of("卷一 本纪第一 高祖", "卷二 本纪第二 太宗上", "卷十七上 本纪第十七上 敬宗　文宗上"),
                result.units().stream().map(TextStructureParser.Unit::path).distinct().toList());
        assertTrue(paths(result).stream().noneMatch(path -> path.contains("/")));
        assertTrue(result.unmatchedVolumes().stream().noneMatch(volume -> volume.startsWith("卷二 ")));
        assertTrue(result.unmatchedHeadings().stream().noneMatch(heading -> heading.contains("卷十七上")));
        assertTrue(result.outline().stream().anyMatch(node -> "卷二 本纪第二 太宗上".equals(node.path())));
        assertTrue(result.outline().stream().noneMatch(node -> "文前".equals(node.path())));
        assertTrue(result.units().stream().noneMatch(unit -> "文前".equals(unit.path()) || unit.text().equals("附录")));
        assertTrue(result.outline().stream().allMatch(node -> node.children().isEmpty()));
    }

    /**
     * 行首普通空格、tab、BOM、全角空格都不妨碍正文标题匹配。
     */
    @Test
    void divergentMatchesBodyTitleAfterSpaceTabBom() {
        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        String toc = "卷一 本纪第一\n卷二 本纪第二\n\n";
        String rest = "本纪第二 太宗上\n　　太宗率长孙无忌伏兵玄武门。\n";
        for (String prefix : List.of(" ", "\t", "\u3000", "\uFEFF", " \t\u3000")) {
            TextStructureParser.ParseResult result = parser.parse(toc + prefix + rest, profile);
            assertEquals(
                    List.of("卷二 本纪第二 太宗上"),
                    paths(result),
                    () -> "prefix codepoints "
                            + prefix.codePoints().mapToObj(Integer::toHexString).toList()
                            + " summary="
                            + result.summary());
        }
    }

    /**
     * 目录与正文同形的不规则篇名：缺「第」、列伟、中间多「卷」，反查卷号仍要命中。
     */
    @Test
    void divergentLooksUpIrregularLiezhuanTitles() {
        String fixture = read("jizhuan-toc-divergent-irregular-pian.txt");
        assertTrue(fixture.contains("\u3000\u3000列传四十五"));
        assertTrue(fixture.contains("\u3000\u3000列伟第四十六"));
        assertTrue(fixture.contains("\u3000\u3000列传八十四"));
        assertTrue(fixture.contains("\u3000\u3000列传卷第一百一十"));

        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        TextStructureParser.ParseResult result = parser.parse(fixture, profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(4, result.headingCount());
        assertEquals(
                List.of(
                        "卷九十五 列传四十五 睿宗诸子",
                        "卷九十六 列伟第四十六",
                        "卷一百三十四 列传八十四",
                        "卷一百六十 列传卷第一百一十"),
                result.units().stream().map(TextStructureParser.Unit::path).distinct().toList());
        assertTrue(paths(result).stream().noneMatch(path -> path.contains("/")));
        assertTrue(result.unmatchedVolumes().isEmpty(), () -> result.unmatchedVolumes().toString());
    }

    /**
     * 丢掉文前目录副本；卷名整串一层 path；非空正文行各成一段，不要求 tab、空行或全角缩进。
     * 卷标题与空行不是 unit。
     */
    @Test
    void sameDropsLeadingTocCopy() {
        String fixture = read("jizhuan-toc-same.txt");
        int tocTitle = fixture.indexOf("卷一 五帝本纪第一");
        int bodyTitle = fixture.lastIndexOf("卷一 五帝本纪第一");
        assertTrue(tocTitle >= 0 && bodyTitle > tocTitle);
        String body = fixture.substring(bodyTitle);
        assertFalse(body.contains("\t"));
        assertFalse(body.contains("\u3000\u3000"));
        assertTrue(body.contains("蚩尤作乱，不用帝命。於是黄帝乃徵师诸侯，与蚩尤战於涿鹿之野，遂禽杀蚩尤。\n而诸侯咸尊轩辕为天子"));

        StructureProfile profile = registry.require("jizhuan-toc-same");
        assertEquals("line", profile.paragraph());
        assertEquals("blank_or_indent", registry.require("jizhuan-toc-divergent").paragraph());
        assertEquals("blank_or_indent", registry.require("biannian-juan-ji-nian").paragraph());

        TextStructureParser.ParseResult result = parser.parse(fixture, profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(2, result.headingCount());
        assertTrue(paths(result).stream().noneMatch(path -> path.contains("/")));
        assertEquals(
                List.of("卷一 五帝本纪第一", "卷二 夏本纪第二"),
                result.units().stream().map(TextStructureParser.Unit::path).distinct().toList());
        long juan1 = count(result, "卷一 五帝本纪第一");
        assertTrue(juan1 > 10, () -> "卷一 unit=" + juan1 + " " + result.summary());
        assertTrue(count(result, "卷二 夏本纪第二") > 1);
        assertTrue(result.units().stream().noneMatch(unit -> unit.text().isBlank()));
        assertFalse(result.units().stream().anyMatch(unit -> unit.text().equals("卷一 五帝本纪第一")
                || unit.text().equals("卷二 夏本纪第二")
                || unit.text().equals("卷三 殷本纪第三")));

        String chiYou = "蚩尤作乱，不用帝命。於是黄帝乃徵师诸侯，与蚩尤战於涿鹿之野，遂禽杀蚩尤。";
        String zunXuanYuan = "而诸侯咸尊轩辕为天子，代神农氏，是为黄帝。天下有不顺者，黄帝从而征之，平者去之，披山通道，未尝宁居。";
        List<String> texts = result.units().stream().map(TextStructureParser.Unit::text).toList();
        int chiYouAt = texts.indexOf(chiYou);
        assertTrue(chiYouAt >= 0, () -> "缺少蚩尤作乱段: " + result.summary());
        assertEquals(zunXuanYuan, texts.get(chiYouAt + 1));
        assertEquals("卷一 五帝本纪第一", result.units().get(chiYouAt).path());
        assertEquals("卷一 五帝本纪第一", result.units().get(chiYouAt + 1).path());
        assertTrue(result.units().stream()
                .noneMatch(unit -> unit.text().contains("蚩尤作乱") && unit.text().contains("而诸侯咸尊轩辕")));

        assertEquals(
                List.of("卷一 五帝本纪第一", "卷二 夏本纪第二"),
                result.outline().stream().map(OutlineTrees.OutlineNode::path).toList());
        assertTrue(result.outline().stream().allMatch(node -> node.children().isEmpty()));
        assertTrue(StructurePaths.skipEdu("卷十三 三代世表第一"));
        assertFalse(StructurePaths.skipEdu("卷一 五帝本纪第一"));
        assertTrue(result.units().stream().noneMatch(unit -> "文前".equals(unit.path())));
        assertFalse(profile.name().contains("史记"));
    }

    /**
     * 空行不是 unit；夹在两行正文之间也不合并、不另计一段。
     */
    @Test
    void sameLineModeSkipsBlankLines() {
        StructureProfile profile = registry.require("jizhuan-toc-same");
        String text = """
                卷一 五帝本纪第一
                卷二 夏本纪第二

                卷一 五帝本纪第一
                蚩尤作乱，不用帝命。於是黄帝乃徵师诸侯，与蚩尤战於涿鹿之野，遂禽杀蚩尤。

                而诸侯咸尊轩辕为天子，代神农氏，是为黄帝。天下有不顺者，黄帝从而征之，平者去之，披山通道，未尝宁居。
                卷二 夏本纪第二
                夏禹，名曰文命。
                """;
        TextStructureParser.ParseResult result = parser.parse(text, profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(3, result.units().size());
        assertEquals("蚩尤作乱，不用帝命。於是黄帝乃徵师诸侯，与蚩尤战於涿鹿之野，遂禽杀蚩尤。", result.units().get(0).text());
        assertEquals(
                "而诸侯咸尊轩辕为天子，代神农氏，是为黄帝。天下有不顺者，黄帝从而征之，平者去之，披山通道，未尝宁居。",
                result.units().get(1).text());
        assertEquals("卷一 五帝本纪第一", result.units().get(0).path());
        assertEquals("卷一 五帝本纪第一", result.units().get(1).path());
        assertEquals("卷二 夏本纪第二", result.units().get(2).path());
        assertTrue(result.units().stream().noneMatch(unit -> unit.text().isBlank()));
    }

    /**
     * 目录异形仍按空行或缩进切段：无缩进的连续行粘成一段，不按行拆开。
     */
    @Test
    void divergentDoesNotSplitByBareLine() {
        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        assertEquals("blank_or_indent", profile.paragraph());
        String text = "卷一 本纪第一\n\n本纪第一 高祖\n高祖神尧。\n二年春正月。\n";
        TextStructureParser.ParseResult result = parser.parse(text, profile);
        assertEquals(1, result.units().size(), result.summary());
        assertTrue(result.units().get(0).text().contains("高祖神尧"));
        assertTrue(result.units().get(0).text().contains("二年春正月"));
    }

    /**
     * 无总目也能建卷→纪→年；点年仍是多段；王名并入元年。
     */
    @Test
    void biannianBuildsJuanJiNianWithMultipleUnitsPerYear() {
        StructureProfile profile = registry.require("biannian-juan-ji-nian");
        TextStructureParser.ParseResult result = parser.parse(read("biannian-juan-ji-nian.txt"), profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals("blank_or_indent", profile.paragraph());
        assertEquals(2, count(result, "卷第一/周纪一/威烈王二十三年"));
        assertEquals(1, count(result, "卷第一/周纪一/威烈王二十四年"));
        assertEquals(1, count(result, "卷第一/周纪一/安王元年"));
        assertEquals(1, count(result, "卷第二/秦纪一/昭襄王五十二年"));
        assertTrue(result.units().stream().noneMatch(unit -> "卷第一/周纪一/威烈王二十三年".equals(unit.path()) && unit.text().contains("初命") && unit.text().contains("臣光曰")));
        assertFalse(profile.name().contains("通鉴"));
        assertFalse(profile.name().contains("资治通鉴"));
    }

    /**
     * 识别不到标题时不得当作可确认结果，也不生成 pN。
     */
    @Test
    void poorRecognitionIsNotAcceptable() {
        StructureProfile profile = registry.require("biannian-juan-ji-nian");
        TextStructureParser.ParseResult result = parser.parse("太宗率长孙无忌伏兵玄武门。\n\n建成、元吉至临湖殿。", profile);
        assertFalse(result.acceptable());
        assertTrue(result.units().stream().noneMatch(unit -> unit.path().startsWith("p")));
    }

    private static List<String> paths(TextStructureParser.ParseResult result) {
        return result.units().stream().map(TextStructureParser.Unit::path).toList();
    }

    private static long count(TextStructureParser.ParseResult result, String path) {
        return result.units().stream().filter(unit -> path.equals(unit.path())).count();
    }

    private static String read(String name) {
        String path = "/structure/" + name;
        try (InputStream in = TextStructureParserTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("缺少切片 " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
