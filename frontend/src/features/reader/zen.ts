/** 字号滑杆下限（px）。 */
export const ZEN_SIZE_MIN = 14
/** 字号滑杆上限（px）。 */
export const ZEN_SIZE_MAX = 40
/** 滑杆默认基准（px）。正文比它大 2px。 */
export const ZEN_SIZE_DEFAULT = 16
/** 三档快捷：小 / 中 / 大。 */
export const ZEN_SIZE_PRESETS = { small: 14, medium: 16, large: 20 } as const

const ME_KEY = 'ontotrace.me'

/** ZeoSeven 思源宋体（Noto Serif CJK SC）unicode-range 分包 CSS。 */
export const NOTO_SERIF_CJK_CSS = 'https://fontsapi.zeoseven.com/285/main/result.css'
/** ZeoSeven 霞鹜文楷等宽（OFL）unicode-range 分包 CSS。 */
export const LXGW_WENKAI_MONO_CSS = 'https://fontsapi.zeoseven.com/293/main/result.css'
/** ZeoSeven 京华老宋（KingHwaOldSong，作者允许嵌入网页）。 */
export const KINGHWA_OLD_SONG_CSS = 'https://fontsapi.zeoseven.com/309/main/result.css'
/** ZeoSeven 上图东观（STDongGuanTi，上图开放共享；只热链不打包）。 */
export const ST_DONGGUAN_CSS = 'https://fontsapi.zeoseven.com/488/main/result.css'

const SONG_FALLBACK = '"Songti SC", "STSong", "SimSun", "NSimSun", serif'

/** 正文字体。webfont 按需热链 ZeoSeven；黑体用系统无衬线。 */
export const ZEN_FONT_OPTIONS = [
  {
    id: 'noto',
    label: '思源宋体',
    stack: `"Noto Serif CJK", ${SONG_FALLBACK}`,
    cssId: 'noto-serif-cjk-font-css',
    href: NOTO_SERIF_CJK_CSS,
  },
  {
    id: 'kinghwa',
    label: '京华老宋',
    stack: `"KingHwaOldSong", ${SONG_FALLBACK}`,
    cssId: 'kinghwa-oldsong-font-css',
    href: KINGHWA_OLD_SONG_CSS,
  },
  {
    id: 'kai',
    label: '霞鹜文楷',
    stack: '"LXGW WenKai Mono", "Kaiti SC", "STKaiti", "KaiTi", serif',
    cssId: 'lxgw-wenkai-mono-font-css',
    href: LXGW_WENKAI_MONO_CSS,
  },
  {
    id: 'dongguan',
    label: '上图东观',
    stack: `"STDongGuanTi", ${SONG_FALLBACK}`,
    cssId: 'stdongguanti-font-css',
    href: ST_DONGGUAN_CSS,
  },
  {
    id: 'hei',
    label: '黑体',
    stack:
      '"PingFang SC", "Hiragino Sans GB", "Heiti SC", "SimHei", "Microsoft YaHei", "Noto Sans CJK SC", system-ui, sans-serif',
    cssId: '',
    href: '',
  },
] as const

export type ZenFontId = (typeof ZEN_FONT_OPTIONS)[number]['id']

/** 背景模式。正常羊皮纸，护眼浅绿；夜间比读通鉴 `#10141b` 浅一档，仍是夜间。 */
export const ZEN_THEME_OPTIONS = [
  { id: 'normal', label: '正常', bg: '#f6f1e7', fg: '#222', muted: '#555' },
  { id: 'eye', label: '护眼', bg: '#f6ffe8', fg: '#222', muted: '#4a5a3c' },
  { id: 'night', label: '夜间', bg: '#1c2430', fg: '#e8e6e1', muted: '#9aa3ad' },
] as const

export type ZenThemeId = (typeof ZEN_THEME_OPTIONS)[number]['id']

export type ZenTheme = (typeof ZEN_THEME_OPTIONS)[number]

/**
 * 阅读壳 / 弹层共用的主题变量。弹层 teleport 到 body，必须自己带一份。
 *
 * @param theme 当前背景
 */
export function zenThemeVars(theme: ZenTheme): Record<string, string> {
  return {
    '--zen-bg': theme.bg,
    '--zen-fg': theme.fg,
    '--zen-muted': theme.muted,
    '--background': theme.bg,
    '--foreground': theme.fg,
    '--muted-foreground': theme.muted,
    '--popover': theme.bg,
    '--popover-foreground': theme.fg,
    '--card': theme.bg,
    '--card-foreground': theme.fg,
    '--muted': `color-mix(in oklab, ${theme.fg} 8%, transparent)`,
    '--border': `color-mix(in oklab, ${theme.fg} 12%, transparent)`,
    '--input': `color-mix(in oklab, ${theme.fg} 12%, transparent)`,
    '--accent': `color-mix(in oklab, ${theme.fg} 10%, ${theme.bg})`,
    '--accent-foreground': theme.fg,
    '--secondary': `color-mix(in oklab, ${theme.fg} 10%, ${theme.bg})`,
    '--secondary-foreground': theme.fg,
    '--ring': `color-mix(in oklab, ${theme.fg} 28%, transparent)`,
  }
}

/** 登录用户的纯净阅读偏好。 */
export interface ZenPrefs {
  size: number
  wide: boolean
  font: ZenFontId
  theme: ZenThemeId
}

export const ZEN_PREFS_DEFAULT: ZenPrefs = {
  size: ZEN_SIZE_DEFAULT,
  wide: false,
  font: 'noto',
  theme: 'normal',
}

/**
 * 把 query / 存储里的 `wide` 收成布尔。仅 `1` 或 `true` 为开。
 *
 * @param raw 原始值
 */
export function parseZenWide(raw: unknown): boolean {
  return raw === true || raw === '1'
}

/**
 * 把字号收成滑杆整数。缺省或非法回落到 16，并钳到 14–40。
 *
 * @param raw 原始值
 */
export function parseZenSize(raw: unknown): number {
  const n =
    typeof raw === 'string' && raw.trim() !== ''
      ? Number(raw)
      : typeof raw === 'number'
        ? raw
        : Number.NaN
  if (!Number.isFinite(n)) {
    return ZEN_SIZE_DEFAULT
  }
  return Math.min(ZEN_SIZE_MAX, Math.max(ZEN_SIZE_MIN, Math.round(n)))
}

/**
 * 正文字体。非法回落到思源宋体。
 *
 * @param raw 原始值
 */
export function parseZenFont(raw: unknown): ZenFontId {
  return ZEN_FONT_OPTIONS.some((item) => item.id === raw) ? (raw as ZenFontId) : 'noto'
}

/**
 * 段间横线中间的编号。index 从 0 起。
 *
 * @param index 当前段
 * @param total 当前目录总段数
 */
export function zenParagraphLabel(index: number, total: number): string {
  return `${index + 1} / ${total}`
}

/** 指针在窗口顶部这么多像素内时显示顶栏。 */
export const ZEN_CHROME_HOTZONE_PX = 48
/** 指针离开热区后延迟隐藏，避免闪。 */
export const ZEN_CHROME_HIDE_MS = 480

/**
 * 顶栏是否应保持显示。阅读配置弹层打开、指针在顶栏上、或落在顶部热区时保持。
 *
 * @param overChrome 指针在顶栏上
 * @param prefsOpen 阅读配置弹层打开
 * @param clientY 指针相对视口的 Y；未知时传 null
 */
export function zenChromeStayOpen(overChrome: boolean, prefsOpen: boolean, clientY: number | null): boolean {
  if (overChrome || prefsOpen) {
    return true
  }
  return clientY != null && clientY <= ZEN_CHROME_HOTZONE_PX
}

/**
 * 背景模式。非法回落到正常。
 *
 * @param raw 原始值
 */
export function parseZenTheme(raw: unknown): ZenThemeId {
  return raw === 'eye' || raw === 'night' ? raw : 'normal'
}

/**
 * 文言正文字号：滑杆基准 + 2（dtj 的 large）。
 *
 * @param base 滑杆值
 */
export function zenBodyFontSize(base: number): number {
  return parseZenSize(base) + 2
}

/**
 * 当前登录用户 id。没有会话则空串，调用方不得拿它当匿名存储键。
 */
export function readSessionUserId(): string {
  if (typeof sessionStorage === 'undefined') {
    return ''
  }
  try {
    const raw = sessionStorage.getItem(ME_KEY)
    if (!raw) {
      return ''
    }
    const me = JSON.parse(raw) as { id?: unknown }
    return typeof me.id === 'string' && me.id ? me.id : ''
  } catch {
    return ''
  }
}

/**
 * 按用户隔离的 localStorage 键。不要对空 userId 调用写入。
 *
 * @param userId 登录用户
 */
export function zenPrefsStorageKey(userId: string): string {
  return `ontotrace.zenPrefs.${userId}`
}

/**
 * 读该用户的阅读偏好。无记录或损坏时回默认。
 *
 * @param userId 登录用户
 */
export function loadZenPrefs(userId: string): ZenPrefs {
  if (!userId || typeof localStorage === 'undefined') {
    return { ...ZEN_PREFS_DEFAULT }
  }
  try {
    const raw = localStorage.getItem(zenPrefsStorageKey(userId))
    if (!raw) {
      return { ...ZEN_PREFS_DEFAULT }
    }
    const parsed = JSON.parse(raw) as Record<string, unknown>
    return {
      size: parseZenSize(parsed.size),
      wide: parseZenWide(parsed.wide),
      font: parseZenFont(parsed.font),
      theme: parseZenTheme(parsed.theme),
    }
  } catch {
    return { ...ZEN_PREFS_DEFAULT }
  }
}

/**
 * 写入该用户的阅读偏好。没有 userId 时不写，避免匿名键冒充多用户。
 *
 * @param userId 登录用户
 * @param prefs 偏好
 */
export function saveZenPrefs(userId: string, prefs: ZenPrefs): void {
  if (!userId || typeof localStorage === 'undefined') {
    return
  }
  localStorage.setItem(
    zenPrefsStorageKey(userId),
    JSON.stringify({
      size: parseZenSize(prefs.size),
      wide: Boolean(prefs.wide),
      font: parseZenFont(prefs.font),
      theme: parseZenTheme(prefs.theme),
    }),
  )
}

/**
 * 纯净阅读路由。入口只带 path，阅读配置走用户偏好，不写进 query。
 *
 * @param documentId 文档
 * @param versionId 版本
 * @param path 当前目录节点
 */
export function zenReadingLocation(documentId: string, versionId: string, path?: string) {
  return {
    path: `/documents/${documentId}/versions/${versionId}/zen`,
    query: path ? { path } : {},
  }
}

/**
 * 按需注入正文字体 webfont。已存在或黑体则跳过。
 *
 * @param font 字体
 */
export function ensureZenFont(font: ZenFontId): void {
  if (typeof document === 'undefined') {
    return
  }
  const option = ZEN_FONT_OPTIONS.find((item) => item.id === font)
  if (!option?.cssId || !option.href) {
    return
  }
  if (document.getElementById(option.cssId)) {
    return
  }
  const link = document.createElement('link')
  link.id = option.cssId
  link.rel = 'stylesheet'
  link.href = option.href
  link.crossOrigin = 'anonymous'
  document.head.appendChild(link)
}

/**
 * 按需注入思源宋体。已存在则跳过。
 */
export function ensureNotoSerifCjk(): void {
  ensureZenFont('noto')
}
