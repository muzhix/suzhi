/** 字号滑杆下限（px）。 */
export const ZEN_SIZE_MIN = 14
/** 字号滑杆上限（px）。 */
export const ZEN_SIZE_MAX = 40
/** 滑杆默认基准（px）。正文比它大 2px。 */
export const ZEN_SIZE_DEFAULT = 16
/** 三档快捷：小 / 中 / 大。 */
export const ZEN_SIZE_PRESETS = { small: 14, medium: 16, large: 20 } as const

const NOTO_CSS_ID = 'noto-serif-cjk-font-css'
/** ZeoSeven 思源宋体（Noto Serif CJK SC）unicode-range 分包 CSS。 */
export const NOTO_SERIF_CJK_CSS = 'https://fontsapi.zeoseven.com/285/main/result.css'

/**
 * 把 query `wide` 收成全宽开关。仅 `1` 为开，缺省或其它值都是窄栏。
 *
 * @param raw 路由 query
 */
export function parseZenWide(raw: unknown): boolean {
  return raw === '1'
}

/**
 * 把 query `size` 收成滑杆整数。缺省或非法回落到 16，并钳到 14–40。
 *
 * @param raw 路由 query
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
 * 文言正文字号：滑杆基准 + 2（dtj 的 large）。
 *
 * @param base 滑杆值
 */
export function zenBodyFontSize(base: number): number {
  return parseZenSize(base) + 2
}

/**
 * 纯净阅读路由。入口不写 size，进页后再把字号落到 query。
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
 * 按需注入思源宋体 webfont。已存在则跳过。
 */
export function ensureNotoSerifCjk(): void {
  if (typeof document === 'undefined') {
    return
  }
  if (document.getElementById(NOTO_CSS_ID)) {
    return
  }
  const link = document.createElement('link')
  link.id = NOTO_CSS_ID
  link.rel = 'stylesheet'
  link.href = NOTO_SERIF_CJK_CSS
  link.crossOrigin = 'anonymous'
  document.head.appendChild(link)
}
