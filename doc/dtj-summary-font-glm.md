# 读通鉴（dutongjian.com）字体与排版样式配置分析

> 分析对象：www.dutongjian.com（《资治通鉴》全文阅读站，Vite + Vue 3 + Element Plus 单页应用）
> 分析方法：抓取线上 CSS bundle（index-*.css，约 695KB）与 JS bundle（index-*.js，约 4MB）及 ZeoSeven Fonts CDN 的字体 CSS，逆向其排版体系。
> 结论：整套排版由 **CSS 变量系统 + 运行时变量映射表** 驱动，字体走 **云端分包 webfont**，简繁转换走 **opencc 词典 + 自定义古籍词典**，行内标记（注音/年龄/干支/生卒）统一用 **`attr()` + 伪元素悬浮** 方案。

---

## 1. 中文字体体系

### 1.1 按内容角色分层的字体变量（核心设计）

站内不使用单一全局字体，而是在 `:root` 定义了**按语义角色划分**的字体栈：

| CSS 变量 | 用途 | 字体栈 |
|---|---|---|
| `--font-family-classical` | 文言正文 | `"Noto Serif CJK", "Songti SC", "STSong", "SimSun", "NSimSun", "Noto Serif CJK SC", serif` |
| `--font-family-translation` | 白话文翻译 | `"LXGW WenKai Mono", "Kaiti SC", "STKaiti", "KaiTi", "KaiTi_GB2312", "BiauKai", "DFKai-SB", "FandolKai", serif` |
| `--font-family-guben-excerpt-fixed` | 古本（元刊本）引文，**不随用户设置变化** | `"KingHwaOldSong", "Songti SC", "STSong", "SimSun", "NSimSun", "Noto Serif CJK SC", serif` |
| `--font-family-ui` | 界面控件 | `"PingFang SC", "Hiragino Sans GB", "Heiti SC", "Microsoft YaHei", "system-ui", sans-serif` |
| `--font-family-modern` | 说明性文字（章钰注条目等） | PingFang/雅黑系 sans 栈 |

设计意图非常清晰：

- **文言正文用衬线宋体**（思源宋体为 webfont 首选，系统宋体兜底）；
- **白话文翻译用楷体**（霞鹜文楷等宽为 webfont 首选，系统楷体兜底），使"文/白"在视觉上天然分区，读者扫一眼就能区分原文与译文；
- **古本引文锁定京華老宋**——刻本风格字体不受用户字体偏好影响，保证"影刻"质感；
- UI 与正文完全分离，保证正文字体/字号调节不污染界面（更新日志原话："字体大小调整只改变主内容，不改UI"）。

### 1.2 用户可选字体与 webfont 加载策略

阅读设置面板"正文字体/白话文字体"提供 5 个选项（正文、白话文可分别设置）：

| 选项 | value | webfont 来源（ZeoSeven Fonts CDN） |
|---|---|---|
| 思源宋体（默认） | `noto` | `fontsapi.zeoseven.com/285/main/result.css`（Noto Serif CJK SC，含 ExtraLight–Black） |
| 京華老宋 | `kinghwa` | `…/309/main/result.css`（KingHwaOldSong v3.000，TerryWang 出品） |
| 霞鹜文楷 | `kai` | `…/293/main/result.css`（LXGW WenKai Mono，OFL 协议；白话文默认） |
| 上图东观 | `dongguan` | `…/488/main/result.css`（STDongGuanTi，上海图书馆版权） |
| 黑体 | `hei` | 无 webfont，纯系统栈 |

加载机制（从 JS 逆向）：

1. **分包按需下载**：CDN 侧字体经 `cn-font-split` 按 `unicode-range` 切成大量小号 woff2 分片，浏览器只下载页面实际用到的字符分片——这是全字库中文字体上线的标准工程做法；
2. **动态注入 `<link>`**：用户选中某字体时，JS 幂等地向 `<head>` 注入 `<link id="…-font-css" rel="stylesheet" href="…" crossOrigin="anonymous">`；
3. **京華老宋在 App 启动时即预载**（写在根组件 setup 里），因为古本引文卡固定使用它，与用户偏好无关；
4. 切换时同时做三件事：注入字体 CSS → 在 `<html>` 上写 `data-reader-font` → 用 `setProperty` 覆写 `--font-family-classical`（或 `--font-family-translation`）→ `localStorage` 持久化。**换字体 = 换一个 CSS 变量**，全部样式自动跟随。

### 1.3 针对具体字体的度量适配（细节亮点）

```css
html[data-reader-font=noto] .place-highlight,
html[data-reader-font=noto] .official-highlight {
  --noto-highlight-frame-inset: calc(
    var(--semantic-highlight-frame-inset-noto) /* .15em */ +
    var(--semantic-highlight-frame-shift-y-noto) /* .03em */
  );
  padding-inline: var(--noto-highlight-frame-inset); …
}
```

思源宋体的**字面率（em-box 内实际字形占比）与宋体/楷体不同**，导致描边框类标记（地点、官职）在思源下会贴字太紧。站方为 `noto` 单独写了框内边距与纵向偏移的补偿规则。换字体不只是换 font-family，**连标记框的几何都跟着调**——这是极少有人做的细节。

### 1.4 简体、繁体、异体字处理

**底本策略**：以繁体（中华书局点校本整理）为数据底本，"简体"是阅读开关（`textMode: "simplified"`），开启后前端实时转换，另有服务端预生成字段 `content_jianti_auto` 作异常兜底。用户纠错体系也按"原文-繁体问题 / 原文-简体问题 / 翻译问题"分类。

**转换引擎（JS 内置，无 opencc 运行时依赖名，但词典为其格式）**：

- 基础转换器：`OpenCC(hk → cn)`（繁→简）与 `OpenCC(cn → hk)`（简→繁，用于用户选中简体字后查康熙字典/百科时反查原字）；
- **自定义古籍词典（最长匹配优先）**，这是简繁质量的关键，词典内容分四类：
  1. **异体字归一**：`郞→郎、衞→卫、幷→并、愼→慎、鬬→斗、屛→屏、慙→惭、敍→叙、塡→填、姪→侄、牋→笺、顚→颠、隄→堤、汚→污、覩→睹、妬→妒、酖→鸩、効→效、噉→啖、犂→犁、煑→煮、諠→喧、穉→稚、緜→绵、咲→笑` 等 40 余组；
  2. **人名专用字保留不转**：`勣（徐勣/李勣）、磾（金日磾）、綝、濛、訢、暐、頵、鄩、縯、璝、頠、諲` 等映射为自身，避免人名被错误类推简化；
  3. **词汇级上下文消歧**（同字不同义）：`報讎/復讎/私讎→报仇` 但 `讎人→仇人`；`乾坤/乾位/乾剛` 保留 *qián*，`孫乾/乾之/乾歸` 转"干"；`徐幹/崔民幹` 人名保留，`渾幹→浑幹`；`反覆/覆按/覆校` 保留 *fù*；`商於`（地名）保留；`徵羽/商徵`（五音 *zhǐ*）保留；
  4. **多字词整体映射**：`開東閤→开东阁、閤道→阁道、尚書閤→尚书阁` 等"閤"字系列。
- 转换算法：对文本做**最长前缀匹配**，命中词典走词表，未命中片段走 opencc 单字转换；HTML 版转换会跳过标签只转文本节点；
- **索引对齐函数**：简繁转换可能改变字符数，站内有专门的"简体索引 ↔ 繁体原文索引"换算（逐字前缀转换计数），保证人物/地点等标注的 `start_index` 在简体模式下仍然对位准确。

**生僻字注音**：5000+ 生僻字配有拼音数据（2026/07/20 上线），渲染方式见 §4.1。

---

## 2. 注释与批注体系

### 2.1 胡三省注（正文夹注，即站内的"脚注"主体）

```css
.hu-note-mark {
  color: var(--color-note) !important;   /* 主题变量：日间 #1565c0 蓝 */
  font-size: var(--reader-font-size-small);  /* 比正文小一档（基准-2px） */
  cursor: pointer; padding: 0 2px; border-radius: 2px;
}
.hu-note-mark:not(.hu-note-expanded):hover { box-shadow: 0 0 0 1px currentColor }
.hu-note-bracket { user-select: none }   /* 注文括号不可选中 */
.hu-note-content  { user-select: text }   /* 注文本身可选中复制 */
```

- 注文**内联在正文之后**，颜色（主题变量 `--color-note`）+ 小一号字号双重视觉降级，读正文时可"视而不见"，读注时可精确扫读；
- **括号与注文的可选中性分离**：复制时不会带出【】类括号，但注文文字可正常选中（更新日志还专门修过"复制时丢日期"的 bug）；
- 支持整站展开/收起（`huNoteMode: expand | collapse`，设置面板"胡三省、章钰批注"开关）；
- 胡注内"事见 xxx / xxx张本"等引文带段落跳转图标（`.hu-note-paragraph-link__icon`，0.9em 内联 SVG）；
- 胡注本身也可被用户批注、可导出。

### 2.2 章钰注（块级尾注/脚注形态）

```css
.zhang-notes { margin-top: 16px; padding-top: 12px;
               border-top: 1px dashed var(--border-color-light) }
.zhang-note-item { padding-left: 12px; border-left: 3px solid …;
                   font-family: var(--font-family-modern); color: var(--color-note) }
.zhang-note（行内形态） { color: var(--color-note); font-style: italic; margin-left: 4px }
```

段后以**虚线分隔 + 左侧细竖线列表**呈现，与胡注的"内联夹注"形成两个层级：胡注随文，章钰注殿后——这正是古籍"双注本"的数字版式。

### 2.3 个人批注（划线笔记）的四种样式

通过 `.annotation-mark-style--{thin|marker|background|text}` + `.annotation-mark-color--{red|blue|yellow|black}` 两组类切换（会员功能）：

| 模式 | 实现 |
|---|---|
| 细线 | `text-decoration: underline; thickness: .06em; underline-offset: .16em; skip-ink: none` |
| 粗线（荧光笔） | `background-image: linear-gradient(transparent 60%, 色 60%…) `——只涂**字身下半 40%**，模拟真荧光笔 |
| 背景 | `background-color: color-mix(色 24%, transparent); border-radius: 2px` |
| 文字 | 直接改文字颜色 |

四色可选：红 `#ff0000`、蓝 `#0000ff`、绿 `#2f8f4e`（名义 yellow）、琥珀 `#b45309`。颜色全部走 CSS 变量（`--highlight-user-annotation`），主题切换时自动适配。批注可直接嵌入原文流内显示（非纯浮层），批注框大小可拖动调节。

### 2.4 文白逐句联动（另一种"划线"）

`.parallel-text-segment`：常驻声明 `text-decoration: underline; thickness: .08em; offset: .3em; skip-ink: none` 但 `text-decoration-line: none`，鼠标悬停或点击对应句子时置为 `underline` 激活——实现"文言↔白话逐句联动提示"，且不改变布局（装饰属性常驻，只开关 line）。

---

## 3. 语义实体标记（人物/地点/官职/书名）

### 3.1 四类实体的默认视觉（"线条"模式）

| 实体 | 类名 | 视觉 |
|---|---|---|
| 人物 | `.person-highlight` | **仅加粗**（`--semantic-person-font-weight: 700`），无底色 |
| 地点 | `.place-highlight` | **1px 实线描边框**（`outline: 1px solid tertiary`，hover 变当前色） |
| 官职 | `.official-highlight` | **1px 虚线描边框**（`outline: 1px dashed color-mix(tertiary 65%)`）——截图中的"虚线框选"即此 |
| 书名 | `.book-highlight` | **琥珀底色**（`rgba(255,191,0,.2)`，占字高 `1em` 的渐变背景层） |

四类实体用同一套背景技巧：`background-image: linear-gradient(var(--bg), var(--bg))` + `background-size: 100% calc(1em + 2px)`——**高亮只覆盖字身高度，不占满整行行距**，行距 2.0 时页面依然干净。hover 时统一叠 `text-shadow: ±.015em currentColor`（伪加粗）+ 联动高亮（`--linked-hover`：鼠标悬停某人物，全文所有提及位置同时高亮）。

### 3.2 四种"语义强调"模式（一键切换全站实体样式）

设置面板提供 颜色 / 线条 / 仅加粗 / 无 四档。每档是一份**完整的 CSS 变量映射表**（JS 对象），切换时逐条 `setProperty` 到 `<html>`：

```js
Ce = {
  line:  { "--semantic-person-bg": "transparent", "--semantic-person-font-weight": "700",
           "--semantic-place-outline": "1px solid var(--text-color-tertiary)",
           "--semantic-book-bg": "var(--highlight-book)",
           "--semantic-official-outline": "1px dashed color-mix(...)", ... },
  color: { "--semantic-person-bg": "var(--highlight-person)" /* 绿底 */,
           "--semantic-place-bg": "var(--highlight-place)" /* 紫底 */,
           "--semantic-official-bg": "color-mix(accent-info-bg 75%)", ... },
  bold:  { 全部 font-weight: 700，无框无底 },
  none:  { 全部无样式，仅 hover 出框 }
}
```

即"实体怎么标"是**变量驱动的四套皮肤**，DOM 结构与类名完全不变——主题（正常/护眼/夜间）同理，也是三份完整变量映射（含 `--color-note`、各高亮色、滚动条色等 40+ 变量）。这是整站最值得借鉴的架构：**样式模式 = CSS 变量快照**。

### 3.3 标注重叠的渲染算法

正文渲染按**字符数组逐字迭代**构建 HTML：

1. 人物/地点/书名/官职标注按 `start_index` 排序，同位者按 `note_priority`（人物=地点=书名 1，官职 2）；
2. **禁止跨实体重叠**：官职与已有区间重叠时直接丢弃（解决"官职错位"问题）；
3. 拼音标注独立成组，可**嵌套**在人物标注内部（人物 span 带 `data-has-pinyin` 联动）；
4. 每个实体 span 携带 `data-id`（跳转）/`data-age`/`data-link-td-id` 等 data 属性，样式与数据分离。

---

## 4. 行内小标记：注音 / 年龄 / 干支 / 生卒

### 4.1 统一的"字上悬浮小标"方案（不用 `<ruby>`）

站内所有"字上方小字"（生僻字拼音、人物年龄、干支序号）用同一套模式：

```css
.pinyin-mark::before {
  content: attr(data-pinyin);          /* 数据放 data 属性 */
  position: absolute; top: .14em; left: 50%;
  transform: translate(-50%, -100%);   /* 悬浮居中于字上方 */
  font-size: var(--reader-font-size-xs);      /* 基准-4px */
  color: var(--text-color-tertiary); line-height: 1;
  pointer-events: none; user-select: none;    /* 不挡点击、不被复制 */
}
.pinyin-mark:hover::before { color: var(--accent-primary) }
```

- `.pinyin-mark::before` → `attr(data-pinyin)` 生僻字注音；
- `.person-highlight[data-age]::before` → `attr(data-age) "岁"` 人物年龄（当前编年 − 生年，0–99 有效）；
- `.ganzhi-mark::before` → `"序" attr(data-ganzhi-index)` 干支纪日序号，点击展开干支表；
- 冲突处理：人名带拼音时年龄标自动上移（`data-has-pinyin="1"` → `top: -.58em`），两个小标不打架；
- 选 `::before + attr()` 而非 `<ruby><rt>` 的收益：**不参与行高计算**（行距 2.0 不被撑乱）、不进复制文本、可整组用 `user-select:none` 静音。

### 4.2 生卒年月标记

```css
.person-life-tag {
  font-size: var(--reader-font-size-xs);   /* 比正文小两档 */
  position: relative; top: .1em;           /* 微升，贴近人名 */
  color: var(--text-color-tertiary);       /* 弱化灰 */
  line-height: var(--line-height-tight);
}
```

- 生成逻辑：同一人物（按 `link_td_id` 去重）**全文只在其首次出现处**追加 `<span class="person-life-tag">前403-前386</span>`；
- 纪年格式化：负年份渲染为"前xxx"，缺失渲染"?"；
- 数据来自维基百科，设置面板明示"生卒数据来自维基百科，仅供参考"；
- 与"生卒"开关联动，关闭则完全不注入。

---

## 5. 行距、字距、字号

### 5.1 相对字号阶梯（一个滑杆驱动全站）

```
基准 slider：14–40px（默认 16，四舍五入钳位）
--reader-font-size-medium  = 基准        （白话文/注块）
--reader-font-size-large   = 基准 + 2    （文言正文）
--reader-font-size-small   = 基准 − 2 (≥10)（胡注）
--reader-font-size-xs      = 基准 − 4 (≥8) （拼音/年龄/生卒小标）
--reader-font-size-heading-1 = 基准 + 8  （段落标题）
```

所有正文相关元素引用变量而非写死 px，因此**调字号时"正文、注文、小标、标题"保持等比缩放**；UI 字号是另一套独立变量（`--font-size-*`: 12/14/15/17/18/24），互不干扰。

### 5.2 行距与字距

```css
.original-text {
  font-family: var(--font-family-classical);
  font-size: var(--reader-font-size-large);
  line-height: var(--reader-body-line-height);       /* 默认 2.0 */
  letter-spacing: var(--reader-body-letter-spacing); /* 默认 0.03em */
  text-indent: 0;
}
```

- 行距默认 **2.0**，会员可调 **1.8–3.0**（步进 0.1）；字距默认 **0.03em**，可调 **0–0.15em**（步进 0.01）；
- 白话文行距 1.8（`--line-height-normal`）、注块 1.8、标题 1.4（`--line-height-heading`）、紧凑 1.5（`--line-height-tight`）——**层级越高行距越紧，正文最松**；
- 实现方式：滑杆直接 `setProperty('--reader-body-line-height', v)` 写到文档根，即时预览、零重排成本；
- 古文阅读的大行距（2.0）+ 微字距（0.03em）组合是"疏可走马"的关键：给字上小标（拼音/年龄）留出了绝对定位的悬浮空间，这也是他们敢用 `::before` 悬浮方案的物理前提。

### 5.3 段落与多版本排版

- `.paragraph-block { margin-bottom: 14px; padding: 0 10px; container-type: inline-size }`——**容器查询**（非媒体查询）驱动"文白左右对照"：`@container (min-width: 600px) { grid 双列 gap 12px }`，侧栏开合、分屏时布局依然正确；
- 通鉴正文**不首行缩进**（段落以"帝与王"/编年语起始，靠段距区分）；纪事本末版式则 `text-indent: 36px; letter-spacing: .05em`（缩进两字 + 更松字距，仿古籍版式）；
- 白话文段落带**左侧 3px 竖线 + padding-left 12px**（引用体），字号小一档、颜色降为 secondary——与文言形成完整的三重区分（字体/字号颜色/左线）；
- 古本（元刊本）引文卡：`writing-mode: vertical-rl` **竖排** + 京華老宋 + 自定义版面变量（`--guben-snippet-width: 18.8%` 等模拟书页天头地脚），全屏查看器支持缩放平移。

---

## 6. 主题系统

三主题（正常 / 护眼 / 夜间）各是一份 40+ 变量的完整映射，与排版相关的要点：

- `--color-note`（胡注蓝）每主题单独调（护眼下 `#2f5f8f`，夜间换暗蓝），保证注文在三种底色上都可读；
- 实体高亮色按主题重配（如夜间人物 hover 用亮黄，护眼用米绿）；
- 连滚动条 thumb/track、按钮、浮层阴影都有主题变量；
- 主题持久化于 localStorage，切换同样只是变量快照替换。

---

## 7. 可系统性借鉴的设计要点

1. **字体按内容角色分层**：classical / translation / ui / guben 四栈分离，"换字体=换一个 CSS 变量"；正文与译文用不同字体族（宋 vs 楷）实现非色彩性的视觉分区。
2. **中文字体走云端分包 webfont**：cn-font-split 按 unicode-range 切片 + `<link>` 按需注入 + 关键固定字体（古本）启动预载，摆脱对系统字体的依赖；并为字面率特殊的字体（思源宋体）单独补偿标记框几何。
3. **简繁转换是词典工程而非字符映射**：opencc 基础上叠"异体字归一 + 人名保留 + 词汇级消歧 + 最长匹配"，并解决转换后的标注索引对齐；查字反查（简→繁）同步实现。
4. **行内小标统一用 `attr() + ::before` 悬浮**：注音/年龄/干支共用一套几何（居中、悬浮、xs 字号、不可选中），互斥避让有显式规则（拼音在场则年龄上移）；不用 `<ruby>` 以保行距稳定与复制纯净。
5. **语义实体四类四态**：人物加粗、地点实线框、官职虚线框、书名底色——用"框型/底色/字重"三个维度编码实体类型，且整站四档语义强调模式 + 三主题全部用 **CSS 变量快照**实现，DOM 零改动。
6. **高亮背景只占字身高度**：`linear-gradient` 单色层 + `background-size: 100% calc(1em + 2px)`，大行距下高亮不糊行。
7. **注释的分级与复制礼仪**：胡注内联（色 + 缩号双降级）、章钰注殿后（虚线分隔 + 左线列表）、个人批注四样式四色；括号 `user-select:none` 而注文可选，复制输出干净。
8. **生卒只标首次出现**：同一人物全文去重，避免"前403-前386"刷屏；年龄动态按编年计算并以 `data-age` 携带。
9. **相对字号阶梯**：一个基准值推导 medium/large/small/xs/heading，调字号全站等比；UI 字号独立，互不污染。
10. **容器查询布局文白对照**：`container-type: inline-size` + `@container` 双列栅格，布局随容器而非视口自适应。

---

## 附：关键变量速查

```css
:root {
  /* 字体 */
  --font-family-classical: "Noto Serif CJK", "Songti SC", "STSong", "SimSun", …, serif;
  --font-family-translation: "LXGW WenKai Mono", "Kaiti SC", "STKaiti", "KaiTi", …, serif;
  --font-family-guben-excerpt-fixed: "KingHwaOldSong", "Songti SC", …, serif;
  /* 字号阶梯（基准 16px 时） */
  --reader-font-size-large: 18px;  --reader-font-size-medium: 15px;
  --reader-font-size-small: 14px;  --reader-font-size-xs: 12px;
  /* 行距/字距 */
  --reader-body-line-height: 2;    --reader-body-letter-spacing: .03em;
  --line-height-heading: 1.4;  --line-height-tight: 1.5;  --line-height-normal: 1.8;  --line-height-loose: 2;
  /* 颜色 */
  --color-note: #1565c0;                        /* 胡注 */
  --highlight-person: rgba(0,255,0,.15);        /* 人物 */
  --highlight-place: rgba(138,43,226,.2);       /* 地点 */
  --highlight-book: rgba(255,191,0,.2);         /* 书名 */
  --highlight-person-hover: rgba(255,255,0,.58);
  /* 语义实体标记度量 */
  --semantic-highlight-bg-height: calc(1em + 2px);
  --semantic-highlight-bg-height-compact: 1em;
  --semantic-highlight-frame-inset-noto: .15em;  /* 思源宋体框补偿 */
  --semantic-highlight-frame-shift-y-noto: .03em;
}
```

*分析日期：2026-09-02；基于当日线上构建（index-7c62ade0.css / index-dacb56bd.js）。*
