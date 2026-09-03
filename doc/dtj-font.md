# 读通鉴字体与阅读排版体系

分析对象：[www.dutongjian.com](https://www.dutongjian.com)（《资治通鉴》全文阅读站）。  
依据：阅读页设置面板与正文截图，线上构建产物 `assets/index-7c62ade0.css`、`assets/index-dacb56bd.js`（约 695KB / 4MB，2026-08-31），以及 ZeoSeven Fonts CDN 的字体 CSS。  
站点是 Vite + Vue 3 + Element Plus 单页应用。整套排版由 **CSS 变量分层 + 运行时整表切换** 驱动，字体走云端分包 webfont，简繁转换走 OpenCC 词典加自定义古籍词表，行内小标（注音 / 年龄 / 干支）统一用 `attr()` + 伪元素悬浮。

---

## 1. 总体设计：按内容角色分字体

站内不使用单一全局字体。界面、文言、白话、古本摘录各走一条字体栈，互不污染。换字体只改对应 CSS 变量，全部样式自动跟随。

| CSS 变量 | 角色 | 默认首选字体 | 视觉意图 |
|---|---|---|---|
| `--font-family-classical` | 文言正文 `.original-text` | Noto Serif CJK（界面名「思源宋体」） | 衬线宋体，适合长时间读古籍 |
| `--font-family-translation` | 白话文 `.paragraph.translation` | LXGW WenKai Mono（界面名「霞鹜文楷」） | 楷体，一眼区分文 / 白 |
| `--font-family-guben-excerpt-fixed` | 古本（元刊本）摘录卡 | KingHwaOldSong（京華老宋） | 刻本气质，**不跟用户正文字体走** |
| `--font-family-ui` | 导航、设置、按钮 | PingFang SC / 微软雅黑 | 现代无衬线，保证控件清晰 |
| `--font-family-modern` | 章钰注条目等说明文字 | 同上无衬线栈 | 注文说明与正文再分一层 |

正文默认样式：

```css
.original-text {
  font-family: var(--font-family-classical);
  font-size: var(--reader-font-size-large);          /* 比滑杆基准大 2px */
  line-height: var(--reader-body-line-height);       /* 默认 2.0 */
  letter-spacing: var(--reader-body-letter-spacing); /* 默认 0.03em */
  text-indent: 0;
}

.paragraph.translation {
  font-family: var(--font-family-translation);
  font-size: var(--reader-font-size-medium);         /* 等于滑杆基准 */
  color: var(--text-color-secondary);
  line-height: var(--line-height-normal);            /* 1.8 */
  padding-left: 12px;
  border-left: 3px solid var(--border-color-light);
}
```

文白对照不只靠颜色。宋 vs 楷、大 2px vs 基准、纯黑 vs 次级灰、白话左侧 3px 竖线，四层信号叠在一起，扫读时不容易串行。颜色留给注文和交互；字体族本身承担分区，色盲、夜间模式、打印时仍然分得清。

更新日志原话：字体大小调整只改变主内容，不改 UI。正文字体 / 字号调节不污染界面控件。

---

## 2. 五套可选字体与 webfont 加载

设置面板里「正文字体」「白话文字体」共用同一组选项，**可以分开选**。默认：正文「思源宋体」，白话「霞鹜文楷」。

| 界面名 | 内部 value | `font-family` 主名 | webfont | 完整回退栈 |
|---|---|---|---|---|
| 思源宋体 | `noto` | `"Noto Serif CJK"` | ZeoSeven `/285/`（Noto Serif CJK SC，可变字重 ExtraLight–Black / 200–900） | Songti SC → STSong → SimSun → NSimSun → Noto Serif CJK SC → serif |
| 京華老宋 | `kinghwa` | `"KingHwaOldSong"` | ZeoSeven `/309/`（TerryWang，v3.000） | 同上宋体回退 |
| 霞鹜文楷 | `kai` | `"LXGW WenKai Mono"` | ZeoSeven `/293/`（霞鹜文楷**等宽** 1.522，OFL） | Kaiti SC → STKaiti → KaiTi → KaiTi_GB2312 → BiauKai → DFKai-SB → FandolKai → serif |
| 上图东观 | `dongguan` | `"STDongGuanTi"` | ZeoSeven `/488/`（上海图书馆 × 汉仪，v1.100） | Songti SC → STSong → SimSun → NSimSun → serif |
| 黑体 | `hei` | 系统无衬线 | **不拉 webfont** | PingFang SC → Hiragino Sans GB → Heiti SC → SimHei → Microsoft YaHei → Noto Sans CJK SC → system-ui |

托管方是 [ZeoSeven Fonts](https://zeoseven.com)（产品日志里写作 ZSFT）。这是整站「不再依赖系统宋体 / 楷体」的前提：手机系统经常缺宋、缺楷。

### 2.1 加载策略

1. **按需注入 `<link>`**。用户选中某字体时，JS 向 `<head>` 插入带固定 `id` 的 stylesheet（如 `noto-serif-cjk-font-css`），已存在则跳过。`crossOrigin="anonymous"`。黑体不走这条路径。
2. **unicode-range 分包**。字体 CSS 由 `cn-font-split` 生成，按码位切成大量小号 woff2（实测约 14–32 个 `@font-face` 分片）。浏览器只下载当前页用到的字。`font-display: swap`。这是全字库中文字体上线的标准工程做法。
3. **京華老宋在 App 根组件启动时预载**。古本摘录卡写死 `--font-family-guben-excerpt-fixed`，必须保证 KingHwaOldSong 随时可用，与用户偏好无关。
4. 切换时同时做四件事：注入 CSS → 在 `<html>` 上写 `data-reader-font="noto|kinghwa|kai|dongguan|hei"` → `setProperty('--font-family-classical'` 或 `'--font-family-translation', stack)` → `localStorage` 持久化（`readerFontFamilyType` / `readerTranslationFontFamilyType`）。

### 2.2 各字体在古文场景里的差异

- **思源宋体**：字面干净、字重可变，适合长读。字面率（em-box 里真正上墨的比例）和其他宋 / 楷不同，框选型标记会贴字太紧，所以站点给 `html[data-reader-font=noto]` 单独做了框的内边距和纵向偏移补偿。换字体不只换 `font-family`，连框的几何也跟着改。
- **京華老宋**：旧印刷宋，字体元数据里带「简 / 日 / 原」字形说明，繁体与旧字形覆盖较好。古本竖排摘录固定用它。
- **霞鹜文楷**：基于 FONTWORKS Klee One 的开源楷体。这里特意用 **Mono 等宽**，文白左右对照时列宽更稳。
- **上图东观**：从《东观余论》《长短经》《唐诗百名家全集》刻本提炼的书卷体，约 9169 字。产品侧明确说过繁体字不足（例如「阬」），缺字时落到宋体回退，同一行可能出现「东观 + 宋体」混排。古文站选字体时，覆盖率优先于风格，风格字体必须有可靠宋体回退。
- **黑体**：现代扫读、屏幕对比度优先，不追求刻本感。

### 2.3 思源宋体的标记框补偿

```css
html[data-reader-font=noto] .place-highlight,
html[data-reader-font=noto] .official-highlight {
  --noto-highlight-frame-inset: calc(
    var(--semantic-highlight-frame-inset-noto) /* .15em */ +
    var(--semantic-highlight-frame-shift-y-noto) /* .03em */
  );
  padding-inline: var(--noto-highlight-frame-inset);
}
```

换 webfont 后框线贴字，是中文排版里很具体的坑。用 `data-reader-font` 做每字体微调，比要求所有字体视觉度量一致更现实。

---

## 3. 简体、繁体、异体字

站点没有单独的「异体字」开关。处理分成三层：底本、转换、字体覆盖。

### 3.1 底本是繁体

全局状态默认 `textMode: "traditional"`。设置面板里的「简体」是一个 checkbox：打开则 `textMode = "simplified"`，关掉就回到繁体底本。界面上没有并列的「繁体」开关——繁体是默认态。

数据侧另有 `content_jianti_auto` 作为服务端预生成简体，转换异常时兜底。用户纠错分类也按「原文-繁体问题 / 原文-简体问题 / 翻译问题」拆开，说明简繁是两套需要分别维护的阅读面。

### 3.2 转换引擎：OpenCC（港繁 → 大陆简）+ 古籍例外词典

前端转换 API 是 `Converter({ from: "hk", to: "cn" })` / `{ from: "cn", to: "hk" }`，即 **opencc-js** 这一路（打包后函数名被 minify）。方向选港繁而不是台繁，更贴近古籍用字（较少台湾词汇替换）。反向转换（简 → 繁）用于选中简体字去查原字、百科、康熙类工具，避免「卫」查不到「衞」。

在 OpenCC 之前，先走一张自定义词典（minify 后名 `KMe`，约 136 条，**最长前缀优先**）。命中词典用词表，未命中片段再交给 OpenCC。HTML 版只转文本节点，标签不动。词典分四类：

**1. 异体 / 旧字形归一（简体模式下生效）**

郞→郎、衞→卫、幷/倂→并、愼→慎、鬬→斗、屛→屏、慙→惭、敍→叙、壻→婿、塡→填、姪→侄、牋→笺、顚→颠、隄→堤、汚→污、覩→睹、妬→妒、酖→鸩、効→效、噉/啗→啖、犂→犁、煑→煮、諠→喧、穉→稚、緜→绵、咲→笑、倣→仿、牓→榜 等 40 余组。

这是「异体字」在阅读层的主要处理：繁体底本保留原形；切到简体时，把刻本常见异体收束到现代规范字，避免读者在简体界面里看到半生不熟的旧字形。

**2. 人名生僻字保护（映射到自身，拦住 OpenCC 乱简化）**

勣、顗、鄩、暐、頵、磾、綝、濛、訢、廞、鉷、縯、璝、頠、諲 等。例如「金日磾」的「磾」、「徐勣 / 李勣」的「勣」不能被类推成别的字。

**3. 一词多义消歧（古文阅读最关键的一层）**

| 字 | 保留本义的上下文 | 允许简化的上下文 |
|---|---|---|
| 徵 | 五音「商徵 / 徵羽」、官职「徵臣」 | 其余走 OpenCC |
| 幹 | 人名「徐幹、崔民幹」 | 其余可简化；「渾幹」只简化左边 |
| 乾 | 「乾坤、乾位、乾剛」保留 *qián* | 人名「孫乾、張乾、乾歸」仍保留「乾」字形，只简化其余部件 |
| 讎 | 「報讎 / 復讎 / 私讎→报仇」一类词 | 单字「讎人→仇人」 |
| 覆 | 「反覆、覆校、覆按」保留 *fù* | 「蕩覆→荡覆」只简化「蕩」 |
| 閤 | 「開東閤→开东阁、閤道→阁道、尚書閤→尚书阁」 | 「鈴閤、閉閤」等部分保留「閤」 |
| 於 | 地名「商於」保留 | 其余可简化 |

原则是：人名、乐律、易学、校勘术语、专名优先保字形，日常用字才简化。

**4. 索引对齐**

人物 / 地点标注带的是繁体底本上的 `start_index`。简繁转换可能改变字符数，所以有前缀长度换算：简体光标位置 ↔ 繁体原文偏移（逐字前缀转换计数）。否则打开「简体」后，划线、人名框、胡注锚点会整体错位。

### 3.3 字体层的异体与缺字

繁体模式下，异体字原样显示，成败取决于字体覆盖：

- 京華老宋：旧字形、繁体较全，最适合「尽量看见底本用字」。
- 思源宋体 SC：简体区字形为主，繁体码位大多仍有字，但「地区字形」（一简一繁同码不同形）可能偏大陆印刷体。
- 上图东观：字数少，繁体 / 生僻缺字会回退到宋体，视觉上会跳一下。

产品日志可以概括为：上图东观好看，但繁体字不够。

---

## 4. 字号、行距、字距

### 4.1 一个滑杆驱动整棵字号树

滑杆范围 **14–40px**，JS 默认 **16**（CSS 变量初值 medium 是 15px，以运行时写入为准）。四舍五入后钳位。写入规则：

| 变量 | 公式 | 用在 |
|---|---|---|
| `--reader-font-size-medium` | 基准 | 白话文、章钰注块 |
| `--reader-font-size-large` | 基准 + 2 | 文言正文 |
| `--reader-font-size-small` | max(基准 − 2, 10) | 胡注 |
| `--reader-font-size-xs` | max(基准 − 4, 8) | 拼音、年龄、生卒、干支小标 |
| `--reader-font-size-heading-1` | 基准 + 8 | 大标题 / 段落标题 |
| `--reader-font-size-heading-2` | 基准 + 2 | 二级标题 |

界面控件另有一套 `--font-size-*`（12 / 14 / 15 / 17 / 18 / 24），**不跟阅读滑杆走**。

基准 16px 时实际是：正文 18px、白话 16px、胡注 14px、小标 12px。截图里滑杆停在 15px，则正文 17px、白话 15px、小标 11px。所有正文相关元素引用变量而非写死 px，调字号时正文、注文、小标、标题保持等比缩放。

### 4.2 行距 2.0、字距 0.03em

| 项 | 默认 | 可调范围 | 步进 | 权限 |
|---|---|---|---|---|
| 行间距 `--reader-body-line-height` | **2.0** | 1.8 – 3.0 | 0.1 | 会员（非会员滑杆 disabled） |
| 字间距 `--reader-body-letter-spacing` | **0.03em** | 0 – 0.15em | 0.01 | 同上 |

行距层级：

- 标题 1.4（`--line-height-heading`）
- 紧凑（生卒小标、UI）1.5（`--line-height-tight`）
- 白话 / 注块 / 普通 1.8（`--line-height-normal`）
- 文言正文 2.0（`--line-height-loose` / `--reader-body-line-height`）

层级越高行距越紧，正文最松。2.0 对屏幕古文几乎是功能需求，不只是审美：生僻字拼音、人物年龄、干支序号都是绝对定位在字顶上方。行距不够，这些小标会叠进上一行。0.03em 的字距则让繁体笔画多的字彼此留出缝，框选、下划线也不容易粘连。

实现上，行距 / 字距作为 CSS 变量挂在文档根或段落容器上，拖滑杆即 `setProperty`，不必重排 DOM。

通鉴编年正文 `text-indent: 0`，靠段距（约 14px）分开。段首常是「二十三年」这类纪年，不适合再缩进。纪事本末等另一套版式更「仿书」：`letter-spacing: .05em; text-indent: 36px`（约两字缩进）。

### 4.3 文白左右对照用容器查询

`.paragraph-block` 设 `margin-bottom: 14px; padding: 0 10px; container-type: inline-size`。宽度 ≥ 600px 时 `.paragraph-main--parallel` 变成两列 grid、列间距 12px：

```css
@container (min-width: 600px) {
  /* grid 双列，gap 12px */
}
```

这跟视口媒体查询不同：侧栏打开、聊天面板变宽时，阅读列自己变窄，对照布局仍然正确。

### 4.4 古本竖排摘录

古本（元刊本）引文卡：`writing-mode: vertical-rl` 竖排 + 京華老宋 + 自定义版面变量（`--guben-snippet-width: 18.8%` 等，模拟书页天头地脚）。全屏查看器支持缩放平移。

---

## 5. 脚注与系统批注（胡三省、章钰）

设置「批注展开」下有两项：

- **胡三省、章钰批注**：`huNoteMode: expand | collapse`，默认收起。
- **我的批注**：`showUserAnnotations`，默认开。

### 5.1 胡注：夹注，不是页脚

胡注插在正文流里，不是文章末尾的脚注列表。结构：

```
〔  .hu-note-bracket（不可选中）
   注文  .hu-note-content
〕  .hu-note-bracket
```

括号是 `〔〕`，不是方括号。样式：

```css
.hu-note-mark {
  color: var(--color-note) !important;          /* 日间 #1565c0 */
  font-size: var(--reader-font-size-small);     /* 比正文小一档 */
  cursor: pointer;
  padding: 0 2px;
  border-radius: 2px;
}
.hu-note-mark:not(.hu-note-expanded):hover {
  box-shadow: 0 0 0 1px currentColor;
}
.hu-note-bracket { user-select: none; }
.hu-note-content { user-select: text; }
```

- 颜色 `--color-note`：日间 `#1565c0`，护眼 `#2f5f8f`，夜间偏灰绿 `#a7ad8c`
- 收起态可点；展开后可选择复制
- 括号 `user-select: none`，复制时不带走括弧；注文本身可选中。更新日志还专门修过「复制时丢日期」的 bug
- 胡注里的「事见某某 / 某某张本」带 `.hu-note-paragraph-link`，图标约 0.9em 内联 SVG，红色 `--huzhu-link-icon-color: #ff0000`，跳到对应段落
- 胡注本身也可被用户批注、可导出

视觉上，胡注靠 **蓝色 + 小一号** 双重降级：读正文可以当装饰掠过，读注时又足够清楚。

### 5.2 章钰注：段后脚注形态

```css
.zhang-notes {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-color-light);
}
.zhang-note-item {
  padding-left: 12px;
  border-left: 3px solid …;
  font-family: var(--font-family-modern);
  color: var(--color-note);
}
.zhang-note {
  color: var(--color-note);
  font-style: italic;
  margin-left: 4px;
}
```

胡注随文、章钰殿后，对应「双注本」的数字分工：高频音义用夹注，校勘、版本用段后注。

---

## 6. 划线、虚线框选、语义强调

截图里同时能看到：人名加粗、地名 / 官职带框、个别词下有蓝线。它们不是同一套样式，而是 **语义实体** 和 **个人批注** 两套系统，外加文白联动下划线，共三种「划线」。

### 6.1 四类实体，默认「线条」模式

全局默认 `semanticEmphasisMode: "line"`。四类实体用框型 / 字重 / 底色三个维度区分，而不是四套完全不同的 DOM。

| 实体 | 类名 | 「线条」模式下的样子 |
|---|---|---|
| 人物 | `.person-highlight` | **只加粗**（`--semantic-person-font-weight: 700`），无框无底 |
| 地点 | `.place-highlight` | **1px 实线描边**（`outline: 1px solid tertiary` / `box-shadow`） |
| 官职 | `.official-highlight` | **1px 虚线描边**（`outline: 1px dashed color-mix(tertiary 65%)`） |
| 书名 | `.book-highlight` | **琥珀底** `rgba(255, 191, 0, .2)` |

截图中的「虚线框选」对应官职实体，不是人名。人名（魏斯、赵籍、韩虔）在线条模式下是加粗；若看到人名带虚线框，多半是该 span 被标成了官职，或当前指针 / 联动高亮。

高亮背景统一写成：

```css
background-image: linear-gradient(var(--entity-highlight-bg), var(--entity-highlight-bg));
background-size: 100% var(--semantic-highlight-bg-height); /* calc(1em + 2px) */
```

高亮只盖字身高度，不铺满 2.0 行距，所以大行距页面不会出现一条条色带。悬停再叠 `text-shadow: ±.015em currentColor`（假加粗）和 `.person-highlight--linked-hover`：同一人物全文其他出现处一起亮（`--linked-hover`）。

### 6.2 语义强调四档（整表切换 CSS 变量）

设置面板提供 颜色 / 线条 / 仅加粗 / 无 四档。每档是一份完整的 CSS 变量映射表（JS 对象），切换时逐条 `setProperty` 到 `<html>`，DOM 类名不变。

| 档 | value | 效果 |
|---|---|---|
| 颜色 | `color` | 人物淡绿底、地点紫底、官职灰蓝底、书名琥珀底 |
| 线条 | `line`（默认） | 见上表：粗 / 实线框 / 虚线框 / 底色 |
| 仅加粗 | `bold` | 四类全部 `font-weight: 700`，无框无底 |
| 无 | `none` | 常态无样式，悬停才出框 |

这和主题（正常 / 护眼 / 夜间）是同一架构：**模式 = CSS 变量快照**。DOM 结构与类名完全不变。

### 6.3 标注重叠的渲染算法

正文渲染按字符数组逐字迭代构建 HTML：

1. 人物 / 地点 / 书名 / 官职标注按 `start_index` 排序，同位者按 `note_priority`（人物 = 地点 = 书名 1，官职 2）；
2. 禁止跨实体重叠：官职与已有区间重叠时直接丢弃（解决「官职错位」问题）；
3. 拼音标注独立成组，可嵌套在人物标注内部（人物 span 带 `data-has-pinyin` 联动）；
4. 每个实体 span 携带 `data-id`（跳转）/ `data-age` / `data-link-td-id` 等 data 属性，样式与数据分离。

### 6.4 个人批注才是真正的「划线」

会员功能。通过 `.annotation-mark-style--{thin|marker|background|text}` + `.annotation-mark-color--{red|blue|yellow|black}` 两组类切换：

| 样式 | value | 实现 |
|---|---|---|
| 细线 | `thin`（默认） | `underline`，粗细 `.06em`，偏移 `.16em`，`skip-ink: none`（下划线不断在字洞里） |
| 粗线（荧光笔） | `marker` | `background-image: linear-gradient(transparent 60%, 色 60%…)`，只涂字身下半 40% |
| 背景 | `background` | `color-mix(色 24%, transparent)`，圆角 2px |
| 文字 | `text` | 直接改字色 |

颜色：红 `#ff0000`、蓝 `#0000ff`、绿 `#2f8f4e`（内部名叫 yellow）、琥珀 `#b45309`（内部名叫 black）。颜色全部走 CSS 变量（`--highlight-user-annotation`），主题切换时自动适配。截图里人名下的细蓝线，就是「细线 + 蓝色」的个人批注，不是语义实体自带的下划线。

批注标记 `.user-annotation-note-mark` 与胡注同构：小一号、可点、悬停描边。展开后批注文字进正文流，批注框大小可拖动调节。

### 6.5 文白联动下划线

`.parallel-text-segment` 预先写好 underline 的颜色、粗细（`.08em`）、偏移（`.3em`），`skip-ink: none`，但默认 `text-decoration-line: none`。悬停联动 / 点击联动激活时才改成 `underline`。布局宽度不变，只开关「画不画线」。这是第三种「划线」：不是实体、也不是笔记，而是对照定位。

---

## 7. 生卒、年龄、拼音、干支：同一套「字上小标」

这些都不使用 `<ruby>`。统一模式：

```css
.pinyin-mark::before {
  content: attr(data-pinyin);
  position: absolute;
  top: .14em;
  left: 50%;
  transform: translate(-50%, -100%);
  font-size: var(--reader-font-size-xs);
  color: var(--text-color-tertiary);
  line-height: 1;
  pointer-events: none;
  user-select: none;
}
.pinyin-mark:hover::before { color: var(--accent-primary); }
```

| 标记 | 类 / 属性 | `::before` 内容 | 默认开关 |
|---|---|---|---|
| 生僻字拼音 | `.pinyin-mark` + `data-pinyin` | 拼音（5000+ 字，2026/07/20 上线） | `showPinyin: true` |
| 人物年龄 | `.person-highlight[data-age]` | `attr(data-age) "岁"` | 随人物实体 |
| 干支序号 | `.ganzhi-mark` + `data-ganzhi-index` | `"序" + 序号`，点击展开干支表 | `showGanzhi: true` |
| 生卒年 | `.person-life-tag`（行内，不是伪元素） | `前396-前338` 这类文本 | `showPeopleLife: true` |

不用 `<ruby>` 的原因：伪元素 **不参与行高**、**不进剪贴板**、可以整组 `user-select: none`。行距 2.0 正好给字顶留出悬浮层。这是他们敢用 `::before` 悬浮方案的物理前提。

冲突处理：人名上同时有拼音时（`data-has-pinyin="1"`），年龄标改为 `top: -.58em`，两个小标错开。

### 7.1 生卒年月

```css
.person-life-tag {
  font-size: var(--reader-font-size-xs);
  position: relative;
  top: .1em;
  color: var(--text-color-tertiary);
  line-height: var(--line-height-tight);
}
```

- 同一人物按 `link_td_id` 去重，**只在全文第一次出现处**追加 `.person-life-tag`
- 负年份写成「前 N」（例如 `?-前396`、`1019-1086`）；缺生或缺卒用 `?`
- 数据来自维基百科，设置里写明「仅供参考」
- 当前编年 − 生年若在 0–99，另外写入 `data-age`，字顶显示「N岁」
- 与「生卒」开关联动，关闭则完全不注入

复制、搜索、对照匹配时，tree walker 会跳过 `.person-life-tag`、`.hu-note-mark`、`.user-annotation-note-mark`，避免「前396」被当成正文。

---

## 8. 主题与颜色体系

三套背景：`normal` / `eye`（护眼） / `night`（夜间）。每套是 40+ 条变量的完整映射，包括正文色、注文蓝、实体高亮、滚动条、按钮、浮层阴影。切换同样只是变量快照替换，持久化于 localStorage。

日间与阅读强相关的颜色：

| 变量 | 日间值 | 用途 |
|---|---|---|
| `--text-color-primary` | `#222` | 文言 |
| `--text-color-secondary` | `#555` | 白话、次级说明 |
| `--text-color-tertiary` | `#777` | 生卒、拼音、干支 |
| `--color-note` | `#1565c0` | 胡注 / 章钰注 |
| `--highlight-person` | `rgba(0,255,0,.15)` | 人物色模式底 |
| `--highlight-place` | `rgba(138,43,226,.2)` | 地点 |
| `--highlight-book` | `rgba(255,191,0,.2)` | 书名 |
| `--highlight-person-hover` | `rgba(255,255,0,.58)` | 悬停 / 联动 |

护眼底 `#f6ffe8`，夜间底 `#10141b`。注文蓝在夜间改成低饱和色，避免蓝字在深色底上发糊。实体高亮色按主题重配（夜间人物 hover 用亮黄，护眼用米绿）。

---

## 9. 设置面板与默认值

截图右侧「批注展开」对应阅读配置。当前代码里的默认状态：

| 分组 | 项 | 默认 |
|---|---|---|
| 批注展开 | 胡三省、章钰批注 | 收起 |
|  | 我的批注 | 开 |
| 正文与白话文 | 简体 | 关（即繁体底本） |
|  | 白话文 | 开 |
|  | 拼音（生僻字） | 开 |
|  | 左右对照 / 悬停联动 / 点击联动 | 开（对照需宽屏） |
| 辅助 | 干支纪日、生卒 | 开 |
| 字体 | 大小 | 16px（截图为用户调到 15px） |
|  | 正文字体 | 思源宋体 |
|  | 白话文字体 | 霞鹜文楷 |
| 背景 | 正常 / 护眼 / 夜间 | 正常 |
| 语义强调 | 颜色 / 线条 / 仅加粗 / 无 | **线条** |
| 高级样式（会员） | 个人批注样式 / 颜色 | 细线 + 红 |
|  | 行间距 / 字间距 | 2.0 / 0.03em |

持久化：字号和正文字体、白话字体进 `localStorage`；语义模式、行距、主题等走全局 Pinia store（并随账号云同步阅读记录，见更新日志）。

---

## 10. 这套做法为什么适合古文阅读站

1. **文白分字体，而不是分颜色。** 宋体正文 + 楷体译文，色盲、夜间模式、打印都还能分清。颜色留给注文和交互。「换字体 = 换一个 CSS 变量」，正文与 UI 完全分离。
2. **简繁是阅读开关，不是两套互不相干的文本。** 底本繁体，简体实时转；例外词典专门保护人名、乐律、易学用字；转换后还要重算标注下标。异体字在繁体里保留、在简体里归一，而不是做一个容易误导的「显示异体」总开关。
3. **webfont 按码位切片。** 中文全字库不可能整包下发。unicode-range + 按需 `<link>` 解决了手机没宋体、没楷体的问题；古本用字再单独预载京華老宋。风格字体必须有可靠宋体回退。
4. **大行距是功能，不是留白。** 2.0 行距给拼音、年龄、干支让出字顶通道；0.03em 字距给框选和下划线让出缝。行距不够，这套伪元素方案会塌。
5. **行内小标统一用 `attr() + ::before` 悬浮。** 注音 / 年龄 / 干支共用一套几何（居中、悬浮、xs 字号、不可选中），互斥避让有显式规则（拼音在场则年龄上移）。不用 `<ruby>` 以保行距稳定与复制纯净。
6. **实体类型用「粗 / 实线框 / 虚线框 / 底色」编码，笔记用下划线编码。** 两套视觉语言分开，读者不会把「这是官职」理解成「我划过的重点」。整站四档语义强调 + 三主题全部用 CSS 变量快照实现，DOM 零改动。
7. **高亮只盖字身。** `background-size: 100% calc(1em + 2px)` 让 2.0 行距仍然透气。
8. **注释分级，复制干净。** 胡注夹在行内（色 + 小号），章钰放段后（虚线 + 左线），个人批注四种笔触。括号不进剪贴板，生卒 / 拼音 / 胡注标记不进复制与搜索。
9. **生卒只标首次。** 通鉴里同一人反复出现，若每处都挂「1019-1086」，版面会被灰字淹没。年龄按当前编年动态计算。
10. **思源宋体单独补框。** 换 webfont 后框线贴字，是中文排版里很具体的坑；用 `data-reader-font` 做每字体微调，比要求所有字体视觉度量一致更现实。
11. **相对字号阶梯。** 一个基准值推导 medium / large / small / xs / heading，调字号全站等比；UI 字号独立，互不污染。
12. **容器查询布局文白对照。** `container-type: inline-size` + `@container` 双列栅格，布局随容器而非视口自适应。侧栏开合时对照仍然正确。
13. **模式切换不改 DOM。** 语义四档、主题三档、批注四样式，全是 CSS 变量快照。这是这套系统能做细、还不崩的结构原因。

---

## 附 A：关键变量速查

```css
:root {
  /* 字体 */
  --font-family-classical: "Noto Serif CJK", "Songti SC", "STSong", "SimSun", …, serif;
  --font-family-translation: "LXGW WenKai Mono", "Kaiti SC", "STKaiti", "KaiTi", …, serif;
  --font-family-guben-excerpt-fixed: "KingHwaOldSong", "Songti SC", …, serif;

  /* 字号阶梯（基准 16px 时） */
  --reader-font-size-large: 18px;
  --reader-font-size-medium: 16px;
  --reader-font-size-small: 14px;
  --reader-font-size-xs: 12px;

  /* 行距 / 字距 */
  --reader-body-line-height: 2;
  --reader-body-letter-spacing: .03em;
  --line-height-heading: 1.4;
  --line-height-tight: 1.5;
  --line-height-normal: 1.8;
  --line-height-loose: 2;

  /* 颜色 */
  --text-color-primary: #222;
  --text-color-secondary: #555;
  --text-color-tertiary: #777;
  --color-note: #1565c0;
  --highlight-person: rgba(0,255,0,.15);
  --highlight-place: rgba(138,43,226,.2);
  --highlight-book: rgba(255,191,0,.2);
  --highlight-person-hover: rgba(255,255,0,.58);

  /* 语义实体标记度量 */
  --semantic-highlight-bg-height: calc(1em + 2px);
  --semantic-highlight-bg-height-compact: 1em;
  --semantic-highlight-frame-inset-noto: .15em;
  --semantic-highlight-frame-shift-y-noto: .03em;
}
```

## 附 B：从截图到 CSS

| 截图上看到的 | 实际机制 |
|---|---|
| 正文偏宋、白话偏楷 | `--font-family-classical` vs `--font-family-translation` |
| 行很疏 | `--reader-body-line-height: 2` |
| 字之间略松 | `--reader-body-letter-spacing: .03em` |
| 「魏斯」后灰色 `?-前396` | `.person-life-tag`，仅首次出现 |
| 人名显得更重 | `.person-highlight` 在「线条」模式下 `font-weight: 700` |
| 虚线方框 | `.official-highlight` 的 dashed outline |
| 细实线方框 | `.place-highlight` 的 solid outline / box-shadow |
| 细蓝下划线 | 个人批注 `.annotation-mark-style--thin` + 蓝色 |
| 蓝色夹注〔…〕 | `.hu-note-mark` / `.hu-note-content`，`--color-note` |
| 字顶拼音 | `.pinyin-mark::before { content: attr(data-pinyin) }` |
| 简体开关 | `textMode` traditional ↔ simplified，OpenCC hk→cn + `KMe` 词典 |

*分析日期：2026-09-02。构建哈希：`index-7c62ade0.css` / `index-dacb56bd.js`。本文合并自 `dtj-summary-font-glm.md` 与 `dtj-summary-font-grok.md`。*
