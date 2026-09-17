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
 * 三套体例切片：目录异形反查卷号、同形去文前目录、编年卷/纪/年且年下多段。
 *
 * @author hanbd
 */
class TextStructureParserTest {

    private final StructureSchemeRegistry registry = new StructureSchemeRegistry();
    private final TextStructureParser parser = new TextStructureParser();

    /**
     * 正文标题反查目录卷号；目录不进正文；篇下按段。
     */
    @Test
    void divergentLooksUpVolumeFromToc() {
        StructureProfile profile = registry.require("jizhuan-toc-divergent");
        TextStructureParser.ParseResult result = parser.parse(read("jizhuan-toc-divergent.txt"), profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals(List.of("本纪/卷一/高祖", "本纪/卷一/高祖", "本纪/卷二/太宗上", "本纪/卷二/太宗上", "列传/卷五十一/后妃上"), paths(result));
        assertFalse(result.units().stream().anyMatch(unit -> unit.path().equals("目录") || unit.text().contains("卷一 本纪第一")));
        assertEquals(2, count(result, "本纪/卷一/高祖"));
        assertTrue(result.unmatchedVolumes().isEmpty());
        assertEquals("纪传体·目录异形", profile.name());
        assertFalse(profile.name().contains("旧唐书"));
    }

    /**
     * 丢掉文前目录副本；表节点可识别；正文卷下多段。
     */
    @Test
    void sameDropsLeadingTocCopy() {
        StructureProfile profile = registry.require("jizhuan-toc-same");
        TextStructureParser.ParseResult result = parser.parse(read("jizhuan-toc-same.txt"), profile);
        assertTrue(result.acceptable(), result.summary());
        assertEquals("卷一/五帝本纪第一", result.units().getFirst().path());
        assertEquals(2, count(result, "卷一/五帝本纪第一"));
        assertEquals(2, count(result, "卷二/夏本纪第二"));
        assertEquals(2, count(result, "卷十三/三代世表第一"));
        assertTrue(StructurePaths.skipEdu("卷十三/三代世表第一"));
        long juanYi = result.units().stream().filter(unit -> unit.path().startsWith("卷一/")).count();
        assertEquals(2, juanYi);
        assertFalse(result.units().stream().anyMatch(unit -> unit.text().equals("卷一 五帝本纪第一")));
        assertFalse(profile.name().contains("史记"));
    }

    /**
     * 无总目也能建卷→纪→年；点年仍是多段；王名并入元年。
     */
    @Test
    void biannianBuildsJuanJiNianWithMultipleUnitsPerYear() {
        StructureProfile profile = registry.require("biannian-juan-ji-nian");
        TextStructureParser.ParseResult result = parser.parse(read("biannian-juan-ji-nian.txt"), profile);
        assertTrue(result.acceptable(), result.summary());
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
