package com.ontotrace.document.parser.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按结构方案从全书文本生成目录树与段级文本单元。一书一套方案，不是一书一个解析器类。
 *
 * @author hanbd
 */
@Slf4j
@Component
public class TextStructureParser {

    private static final Pattern WS = Pattern.compile("[\\s　]+");
    private static final Pattern CATEGORY_LINE = Pattern.compile("^(本纪|本紀|世家|列传|列傳|表|书|書|志|目录|目錄)$");
    private static final Pattern UNMATCHED_HINT = Pattern.compile("卷|第|纪|紀|年|【|●");

    /**
     * 解析结果。
     *
     * @param units 正文段落
     * @param outline 目录树
     * @param unmatchedHeadings 未识别但像标题的行
     * @param unmatchedVolumes 目录有、正文未对上的卷
     * @param headingCount 命中的标题行数
     * @param tocLineCount 目录行数
     * @param acceptable 是否允许确认落版本
     * @param warnings 诊断
     */
    public record ParseResult(
            List<Unit> units,
            List<OutlineTrees.OutlineNode> outline,
            List<String> unmatchedHeadings,
            List<String> unmatchedVolumes,
            int headingCount,
            int tocLineCount,
            boolean acceptable,
            List<String> warnings) {
        /**
         * 预览摘要。
         *
         * @return 短文本
         */
        public String summary() {
            return "标题 " + headingCount + "，段落 " + units.size() + "，未对照卷 " + unmatchedVolumes.size()
                    + "，未识别标题 " + unmatchedHeadings.size();
        }
    }

    /**
     * 一段正文。
     *
     * @param path 结构路径，不含公元、干支
     * @param text 展示文本
     * @param ceYear 公元纪年，阿拉伯数字；没有则为 null
     * @param ganzhi 干支；没有则为 null
     */
    public record Unit(String path, String text, Integer ceYear, String ganzhi) {
        /**
         * 无年附注的段落。
         *
         * @param path 结构路径
         * @param text 展示文本
         */
        public Unit(String path, String text) {
            this(path, text, null, null);
        }
    }

    /**
     * 按方案解析全文。识别过差时 {@code acceptable} 为 false，调用方不得退化成 {@code pN}。
     *
     * @param text 标准化全文
     * @param profile 结构方案
     * @return 解析结果
     */
    public ParseResult parse(String text, StructureProfile profile) {
        String normalizedText = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        String[] lines = normalizedText.split("\n", -1);
        List<CompiledHeading> headings = compile(profile);
        Toc toc = extractToc(lines, profile);
        int bodyStart = bodyStart(lines, profile, toc, headings);
        State state = new State(profile, toc);
        if (bodyStart > 0 && !toc.volumeByKey.isEmpty()) {
            log.debug("structure toc lines={} bodyStart={}", toc.lines.size(), bodyStart);
        }
        for (int i = bodyStart; i < lines.length; i++) {
            consume(lines[i], headings, state);
        }
        state.flush();
        List<String> unmatchedVolumes = profile.toc() != null && profile.toc().lookupVolume()
                ? unmatchedVolumes(toc, state.usedTocKeys)
                : List.of();
        List<OutlineTrees.PathNote> notes = state.units.stream()
                .map(unit -> new OutlineTrees.PathNote(unit.path(), unit.ceYear(), unit.ganzhi()))
                .toList();
        boolean acceptable = acceptable(profile, state, toc, unmatchedVolumes);
        List<String> warnings = new ArrayList<>(state.warnings);
        if (!acceptable) {
            warnings.add("结构识别过差，确认前请改方案。不得按空行退化成 p1/p2。");
        }
        log.info(
                "parsed structure scheme={} paragraph={} headings={} units={} unmatchedVol={} unmatchedHead={} acceptable={}",
                profile.id(),
                profile.paragraph(),
                state.headingCount,
                state.units.size(),
                unmatchedVolumes.size(),
                state.unmatchedHeadings.size(),
                acceptable);
        return new ParseResult(
                List.copyOf(state.units),
                OutlineTrees.fromPathNotes(notes),
                List.copyOf(state.unmatchedHeadings),
                unmatchedVolumes,
                state.headingCount,
                toc.lines.size(),
                acceptable,
                List.copyOf(warnings));
    }

    private void consume(String raw, List<CompiledHeading> headings, State state) {
        if (raw == null) {
            return;
        }
        String trimmed = trimLine(raw);
        if (trimmed.isEmpty()) {
            if (state.blankSplits()) {
                state.flush();
            }
            return;
        }
        CompiledHeading heading = matchHeading(trimmed, headings);
        if (heading != null) {
            state.flush();
            applyHeadingLine(heading, headings, state);
            return;
        }
        if (looksUnmatchedHeading(trimmed)) {
            if (state.unmatchedHeadings.size() < 50) {
                state.unmatchedHeadings.add(trimmed);
            }
            return;
        }
        if (state.lineSplits()) {
            state.flush();
            state.append(trimmed);
            return;
        }
        if (state.indentSplits() && startsWithIndent(raw)) {
            state.flush();
            state.append(stripIndent(raw));
            return;
        }
        state.append(trimmed);
    }

    /**
     * 正文起点。目录同形从第二份卷题起算；目录异形跳过目录结束后的「附录」等残行；编年体无目录时跳过书名等文前残行。都从第一条正文标题起算，避免默认 path「文前」。
     *
     * @param lines 全文行
     * @param profile 结构方案
     * @param toc 已抽出的目录
     * @param headings 编译后的标题规则
     * @return 正文起始下标
     */
    private static int bodyStart(String[] lines, StructureProfile profile, Toc toc, List<CompiledHeading> headings) {
        int start = toc.endIndex;
        if (profile.toc() != null && profile.toc().dropCopy() && !toc.lines.isEmpty()) {
            String first = toc.lines.getFirst();
            int seen = 0;
            for (int i = 0; i < lines.length; i++) {
                if (first.equals(normalize(lines[i]))) {
                    seen++;
                    if (seen == 2) {
                        return i;
                    }
                }
            }
            toc.missingCopy = true;
            start = toc.endIndex;
        }
        if (profile.toc() != null && profile.toc().lookupVolume() && !toc.lines.isEmpty()) {
            return skipToFirstHeading(lines, start, headings);
        }
        if (profile.toc() == null || !profile.toc().enabled()) {
            return skipToFirstHeading(lines, start, headings);
        }
        return start;
    }

    /**
     * 从 {@code start} 扫到第一条正文标题。中间的书名、「附录」等残行不进 unit。
     *
     * @param lines 全文行
     * @param start 目录结束下标，无目录时为 0
     * @param headings 标题规则
     * @return 第一条标题的下标；若没有标题则仍从 {@code start} 起，以免整书被丢掉
     */
    private static int skipToFirstHeading(String[] lines, int start, List<CompiledHeading> headings) {
        for (int i = start; i < lines.length; i++) {
            if (matchHeading(trimLine(lines[i]), headings) != null) {
                if (i > start) {
                    log.debug("skip toc residue lines={} bodyStart={}", i - start, i);
                }
                return i;
            }
        }
        return start;
    }

    private Toc extractToc(String[] lines, StructureProfile profile) {
        Toc toc = new Toc();
        if (profile.toc() == null || !profile.toc().enabled() || profile.toc().linePattern() == null) {
            return toc;
        }
        Pattern linePat = Pattern.compile(profile.toc().linePattern());
        int i = 0;
        while (i < lines.length && trimLine(lines[i]).isEmpty()) {
            i++;
        }
        int skipped = 0;
        while (i < lines.length && skipped < 6) {
            String n = normalize(lines[i]);
            if (n.isEmpty() || linePat.matcher(n).matches()) {
                break;
            }
            if (n.length() <= 40) {
                i++;
                skipped++;
                continue;
            }
            break;
        }
        while (i < lines.length) {
            String n = normalize(lines[i]);
            if (n.isEmpty()) {
                int j = i + 1;
                while (j < lines.length && trimLine(lines[j]).isEmpty()) {
                    j++;
                }
                if (j < lines.length && linePat.matcher(normalize(lines[j])).matches()) {
                    i = j;
                    continue;
                }
                break;
            }
            Matcher m = linePat.matcher(n);
            if (m.matches()) {
                toc.lines.add(n);
                String volume = groupOr(m, 1, n);
                String key = normalize(groupOr(m, 2, n));
                toc.volumeByKey.put(key, volume);
                i++;
                continue;
            }
            if (CATEGORY_LINE.matcher(n).matches()) {
                i++;
                continue;
            }
            break;
        }
        toc.endIndex = i;
        return toc;
    }

    private static boolean acceptable(StructureProfile profile, State state, Toc toc, List<String> unmatchedVolumes) {
        if (state.units.isEmpty() || state.headingCount == 0) {
            return false;
        }
        if (profile.toc() != null && profile.toc().dropCopy() && toc.missingCopy) {
            return false;
        }
        if (profile.toc() != null && profile.toc().lookupVolume() && toc.lines.size() >= 4) {
            return unmatchedVolumes.size() * 2 <= toc.lines.size();
        }
        return true;
    }

    private static List<String> unmatchedVolumes(Toc toc, Set<String> usedKeys) {
        List<String> unmatched = new ArrayList<>();
        for (Map.Entry<String, String> e : toc.volumeByKey.entrySet()) {
            if (!usedKeys.contains(e.getKey())) {
                unmatched.add(e.getValue() + " " + e.getKey());
            }
        }
        return unmatched;
    }

    private static List<CompiledHeading> compile(StructureProfile profile) {
        List<CompiledHeading> compiled = new ArrayList<>();
        for (StructureProfile.HeadingRule rule : profile.headingRules()) {
            compiled.add(new CompiledHeading(rule, Pattern.compile(rule.pattern())));
        }
        return compiled;
    }

    /**
     * 先压本行标题；若是帝号前缀，再把余下当年号标题压栈。
     *
     * @param heading 已匹配的标题
     * @param headings 全部规则
     * @param state 解析状态
     */
    private static void applyHeadingLine(CompiledHeading heading, List<CompiledHeading> headings, State state) {
        state.applyHeading(heading);
        if (heading.remainder == null || heading.remainder.isBlank()) {
            return;
        }
        CompiledHeading rest = matchFullHeading(heading.remainder, headings);
        if (rest != null) {
            state.applyHeading(rest);
            return;
        }
        if (looksUnmatchedHeading(heading.remainder) && state.unmatchedHeadings.size() < 50) {
            state.unmatchedHeadings.add(heading.remainder);
        }
    }

    private static CompiledHeading matchHeading(String line, List<CompiledHeading> headings) {
        if (line == null || line.isBlank()) {
            return null;
        }
        for (CompiledHeading heading : headings) {
            Matcher matcher = heading.pattern.matcher(line);
            if (heading.rule.matchPrefix()) {
                if (!matcher.lookingAt() || matcher.end() == 0) {
                    continue;
                }
                String rest = trimLine(line.substring(matcher.end()));
                if (!rest.isEmpty() && matchFullHeading(rest, headings) == null) {
                    continue;
                }
                return heading.bound(matcher, rest);
            }
            if (matcher.matches()) {
                return heading.bound(matcher, "");
            }
        }
        return null;
    }

    /**
     * 整行匹配，不走前缀规则。用于帝号切开后的年号余下。
     *
     * @param line 余下行
     * @param headings 标题规则
     * @return 命中的规则；没有则为 null
     */
    private static CompiledHeading matchFullHeading(String line, List<CompiledHeading> headings) {
        for (CompiledHeading heading : headings) {
            if (heading.rule.matchPrefix()) {
                continue;
            }
            Matcher matcher = heading.pattern.matcher(line);
            if (matcher.matches()) {
                return heading.bound(matcher, "");
            }
        }
        return null;
    }

    private static boolean looksUnmatchedHeading(String line) {
        return line.length() <= 40 && !line.contains("。") && UNMATCHED_HINT.matcher(line).find();
    }

    private static boolean startsWithIndent(String raw) {
        return raw.startsWith("　　") || raw.startsWith("\u3000\u3000");
    }

    private static String stripIndent(String raw) {
        if (startsWithIndent(raw)) {
            return trimLine(raw.substring(2));
        }
        return trimLine(raw);
    }

    /**
     * 去掉 BOM 与行首行尾空白后再做标题匹配。{@link String#trim()} 不去掉全角空格 U+3000。
     *
     * @param raw 原始行，可为 null
     * @return 去掉 BOM、空格、tab、全角空格后的文本
     */
    private static String trimLine(String raw) {
        return raw == null ? "" : raw.replace("\uFEFF", "").strip();
    }

    static String normalize(String raw) {
        String trimmed = trimLine(raw);
        return WS.matcher(trimmed).replaceAll(" ");
    }

    private static String groupOr(Matcher matcher, int index, String fallback) {
        if (matcher.groupCount() >= index) {
            String g = matcher.group(index);
            if (g != null && !g.isBlank()) {
                return g;
            }
        }
        return fallback;
    }

    /**
     * 用正则分组和目录反查结果填模板。
     *
     * @param template 模板，{@code {tocVolume}} 为卷号，{@code {tocKey}} 为目录篇名
     * @param matcher 当前标题
     * @param tocVolume 目录卷号，可空
     * @param tocKey 目录篇名，可空
     * @return 替换后的文本
     */
    static String interpolate(String template, Matcher matcher, String tocVolume, String tocKey) {
        if (template == null || template.isBlank()) {
            return matcher.group();
        }
        String out = template;
        out = out.replace("{tocVolume}", tocVolume == null ? "" : tocVolume);
        out = out.replace("{tocKey}", tocKey == null ? "" : tocKey);
        for (int i = matcher.groupCount(); i >= 1; i--) {
            String g = matcher.group(i);
            out = out.replace("$" + i, g == null ? "" : g);
        }
        return out.replace("$0", matcher.group()).trim();
    }

    /**
     * 公元用逐位中文数字，如「三六」为 36、「一九四」为 194，不是「三十六」那种进位写法。
     *
     * @param raw 中文或阿拉伯数字串
     * @return 阿拉伯数字；无法识别则为 null
     */
    static Integer chinesePositionalYear(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int n = 0;
        for (int i = 0; i < raw.length(); i++) {
            int digit = switch (raw.charAt(i)) {
                case '〇', '零', '0' -> 0;
                case '一', '1' -> 1;
                case '二', '2' -> 2;
                case '三', '3' -> 3;
                case '四', '4' -> 4;
                case '五', '5' -> 5;
                case '六', '6' -> 6;
                case '七', '7' -> 7;
                case '八', '8' -> 8;
                case '九', '9' -> 9;
                default -> -1;
            };
            if (digit < 0) {
                return null;
            }
            n = n * 10 + digit;
        }
        return n;
    }

    /**
     * 年号缺「年」时补上，如「建武十二」→「建武十二年」。
     *
     * @param label 年标签
     * @return 以「年」结尾的标签
     */
    static String ensureYearSuffix(String label) {
        if (label == null || label.isBlank() || label.endsWith("年")) {
            return label;
        }
        char last = label.charAt(label.length() - 1);
        if ("一二三四五六七八九十百".indexOf(last) >= 0) {
            return label + "年";
        }
        return label;
    }

    private static final class Toc {
        private final Map<String, String> volumeByKey = new LinkedHashMap<>();
        private final List<String> lines = new ArrayList<>();
        private int endIndex;
        private boolean missingCopy;
    }

    private static final class CompiledHeading {
        private final StructureProfile.HeadingRule rule;
        private final Pattern pattern;
        private Matcher matcher;
        private String remainder = "";

        private CompiledHeading(StructureProfile.HeadingRule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
        }

        private CompiledHeading bound(Matcher matcher, String remainder) {
            CompiledHeading copy = new CompiledHeading(rule, pattern);
            copy.matcher = matcher;
            copy.remainder = remainder == null ? "" : remainder;
            return copy;
        }
    }

    private static final class Frame {
        private final int level;
        private final String label;

        private Frame(int level, String label) {
            this.level = level;
            this.label = label;
        }
    }

    private static final class State {
        private final StructureProfile profile;
        private final Toc toc;
        private final Deque<Frame> stack = new ArrayDeque<>();
        private final StringBuilder para = new StringBuilder();
        private final List<Unit> units = new ArrayList<>();
        private final List<String> unmatchedHeadings = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Set<String> usedTocKeys = new LinkedHashSet<>();
        private String currentPath = "文前";
        private String pendingWang;
        private Integer currentCeYear;
        private String currentGanzhi;
        private int headingCount;

        private State(StructureProfile profile, Toc toc) {
            this.profile = profile;
            this.toc = toc;
        }

        private boolean blankSplits() {
            String mode = profile.paragraph();
            return mode == null || mode.startsWith("blank") || "blank_or_indent".equals(mode);
        }

        private boolean indentSplits() {
            String mode = profile.paragraph();
            return "indent".equals(mode) || "blank_or_indent".equals(mode);
        }

        /**
         * 非空正文行各成一段，不要求 tab、空行或全角缩进。
         *
         * @return 按行切段返回 true
         */
        private boolean lineSplits() {
            return "line".equals(profile.paragraph());
        }

        private void append(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            if (!para.isEmpty()) {
                para.append(text.matches("^[\\u4e00-\\u9fff【《].*") ? "" : " ");
            }
            para.append(text);
        }

        private void flush() {
            String text = para.toString().trim();
            para.setLength(0);
            if (text.isEmpty()) {
                return;
            }
            String path = currentPath == null || currentPath.isBlank() ? "文前" : currentPath;
            units.add(new Unit(path, text, currentCeYear, currentGanzhi));
        }

        private void applyHeading(CompiledHeading heading) {
            headingCount++;
            Matcher matcher = heading.matcher;
            StructureProfile.HeadingRule rule = heading.rule;
            String tocVolume = null;
            String tocKey = null;
            if (rule.tocKeyTemplate() != null && profile.toc() != null && profile.toc().lookupVolume()) {
                tocKey = normalize(interpolate(rule.tocKeyTemplate(), matcher, null, null));
                tocVolume = toc.volumeByKey.get(tocKey);
                if (tocVolume != null) {
                    usedTocKeys.add(tocKey);
                } else {
                    warnings.add("目录未对照：" + tocKey);
                    tocVolume = tocKey;
                }
            }
            String label = interpolate(rule.label() == null ? "$0" : rule.label(), matcher, tocVolume, tocKey);
            boolean yearHeading = "nian".equals(rule.nodeType());
            if (pendingWang != null && yearHeading && !rule.mergeYear() && (label.equals("元年") || label.startsWith("元年"))) {
                label = pendingWang + label;
            }
            pendingWang = rule.mergeYear() ? label : null;
            if (yearHeading && !rule.mergeYear()) {
                label = ensureYearSuffix(label);
            }
            currentCeYear = null;
            currentGanzhi = null;
            if (yearHeading && !rule.mergeYear()) {
                if (rule.ceYearGroup() != null && matcher.groupCount() >= rule.ceYearGroup()) {
                    currentCeYear = chinesePositionalYear(matcher.group(rule.ceYearGroup()));
                }
                if (rule.ganzhiGroup() != null && matcher.groupCount() >= rule.ganzhiGroup()) {
                    String ganzhi = matcher.group(rule.ganzhiGroup());
                    currentGanzhi = ganzhi == null || ganzhi.isBlank() ? null : ganzhi;
                }
                if (currentCeYear != null || currentGanzhi != null) {
                    log.debug("nian note path-label={} ceYear={} ganzhi={}", label, currentCeYear, currentGanzhi);
                }
            }
            if (rule.replacesPath()) {
                List<String> segs = new ArrayList<>();
                for (String template : rule.pathTemplate()) {
                    String part = interpolate(template, matcher, tocVolume, tocKey);
                    if (part != null && !part.isBlank()) {
                        segs.add(part);
                    }
                }
                currentPath = String.join("/", segs);
                stack.clear();
            } else if (rule.foldsIntoParent() && !stack.isEmpty()) {
                Frame parent = stack.removeLast();
                String folded = parent.label + "（" + label + "）";
                stack.addLast(new Frame(parent.level, folded));
                log.debug("fold heading {} into {}", label, folded);
                rebuildPath();
            } else {
                int level = rule.stackLevel();
                while (!stack.isEmpty() && stack.peekLast().level >= level) {
                    stack.removeLast();
                }
                stack.addLast(new Frame(level, label));
                rebuildPath();
            }
            if (currentPath.isBlank()) {
                currentPath = "文前";
            }
            if (tocKey != null) {
                log.debug("heading path={} tocVolume={} tocKey={}", currentPath, tocVolume, tocKey);
            }
        }

        private void rebuildPath() {
            List<String> segs = new ArrayList<>();
            for (Frame frame : stack) {
                segs.add(frame.label);
            }
            currentPath = String.join("/", segs);
        }
    }
}
