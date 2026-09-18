import { afterEach, describe, expect, it } from 'vitest'
import {
  ensureNotoSerifCjk,
  ensureZenFont,
  KINGHWA_OLD_SONG_CSS,
  loadZenPrefs,
  LXGW_WENKAI_MONO_CSS,
  NOTO_SERIF_CJK_CSS,
  parseZenFont,
  parseZenSize,
  parseZenTheme,
  parseZenWide,
  readSessionUserId,
  saveZenPrefs,
  ST_DONGGUAN_CSS,
  zenBodyFontSize,
  zenChromeStayOpen,
  zenParagraphLabel,
  zenPrefsStorageKey,
  zenReadingLocation,
  zenThemeVars,
  ZEN_CHROME_HOTZONE_PX,
  ZEN_PREFS_DEFAULT,
  ZEN_SIZE_DEFAULT,
  ZEN_SIZE_PRESETS,
  ZEN_THEME_OPTIONS,
} from './zen'

describe('zen reading helpers', () => {
  afterEach(() => {
    document.getElementById('noto-serif-cjk-font-css')?.remove()
    document.getElementById('lxgw-wenkai-mono-font-css')?.remove()
    document.getElementById('kinghwa-oldsong-font-css')?.remove()
    document.getElementById('stdongguanti-font-css')?.remove()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('clamps slider size and keeps body 2px larger', () => {
    expect(parseZenSize(undefined)).toBe(ZEN_SIZE_DEFAULT)
    expect(parseZenSize('')).toBe(ZEN_SIZE_DEFAULT)
    expect(parseZenSize('16')).toBe(16)
    expect(parseZenSize('16.4')).toBe(16)
    expect(parseZenSize(99)).toBe(40)
    expect(parseZenSize(10)).toBe(14)
    expect(zenBodyFontSize(16)).toBe(18)
    expect(zenBodyFontSize(40)).toBe(42)
    expect(ZEN_SIZE_PRESETS).toEqual({ small: 14, medium: 16, large: 20 })
  })

  it('parses wide, font and theme with safe fallbacks', () => {
    expect(parseZenWide(undefined)).toBe(false)
    expect(parseZenWide('1')).toBe(true)
    expect(parseZenWide(true)).toBe(true)
    expect(parseZenFont('kai')).toBe('kai')
    expect(parseZenFont('hei')).toBe('hei')
    expect(parseZenFont('kinghwa')).toBe('kinghwa')
    expect(parseZenFont('dongguan')).toBe('dongguan')
    expect(parseZenFont('comic')).toBe('noto')
    expect(parseZenTheme('eye')).toBe('eye')
    expect(parseZenTheme('night')).toBe('night')
    expect(parseZenTheme('sepia')).toBe('normal')
  })

  it('labels each paragraph as current / total', () => {
    expect(zenParagraphLabel(0, 29)).toBe('1 / 29')
    expect(zenParagraphLabel(28, 29)).toBe('29 / 29')
  })

  it('keeps zen chrome while pointer is in the top hotzone', () => {
    expect(zenChromeStayOpen(false, false, 10)).toBe(true)
    expect(zenChromeStayOpen(false, false, ZEN_CHROME_HOTZONE_PX)).toBe(true)
    expect(zenChromeStayOpen(false, false, ZEN_CHROME_HOTZONE_PX + 1)).toBe(false)
    expect(zenChromeStayOpen(true, false, 200)).toBe(true)
    expect(zenChromeStayOpen(false, true, 200)).toBe(true)
    expect(zenChromeStayOpen(false, false, null)).toBe(false)
  })

  it('keeps night darker than paper and lighter than #10141b', () => {
    const night = ZEN_THEME_OPTIONS.find((item) => item.id === 'night')
    expect(night?.bg).toBe('#1c2430')
    const vars = zenThemeVars(night!)
    expect(vars['--background']).toBe('#1c2430')
    expect(vars['--popover']).toBe('#1c2430')
    expect(vars['--foreground']).toBe('#e8e6e1')
  })

  it('builds the shell-less zen path without prefs in the query', () => {
    expect(zenReadingLocation('d1', 'v1')).toEqual({
      path: '/documents/d1/versions/v1/zen',
      query: {},
    })
    expect(zenReadingLocation('d1', 'v1', '本纪/卷一')).toEqual({
      path: '/documents/d1/versions/v1/zen',
      query: { path: '本纪/卷一' },
    })
  })

  it('injects song kai kinghwa dongguan once and skips hei', () => {
    ensureNotoSerifCjk()
    ensureZenFont('noto')
    ensureZenFont('kai')
    ensureZenFont('kai')
    ensureZenFont('kinghwa')
    ensureZenFont('kinghwa')
    ensureZenFont('dongguan')
    ensureZenFont('hei')
    expect(document.querySelectorAll('#noto-serif-cjk-font-css')).toHaveLength(1)
    expect(document.querySelectorAll('#lxgw-wenkai-mono-font-css')).toHaveLength(1)
    expect(document.querySelectorAll('#kinghwa-oldsong-font-css')).toHaveLength(1)
    expect(document.querySelectorAll('#stdongguanti-font-css')).toHaveLength(1)
    const noto = document.getElementById('noto-serif-cjk-font-css') as HTMLLinkElement
    const kai = document.getElementById('lxgw-wenkai-mono-font-css') as HTMLLinkElement
    const kinghwa = document.getElementById('kinghwa-oldsong-font-css') as HTMLLinkElement
    const dongguan = document.getElementById('stdongguanti-font-css') as HTMLLinkElement
    expect(noto.getAttribute('href')).toBe(NOTO_SERIF_CJK_CSS)
    expect(kai.getAttribute('href')).toBe(LXGW_WENKAI_MONO_CSS)
    expect(kinghwa.getAttribute('href')).toBe(KINGHWA_OLD_SONG_CSS)
    expect(dongguan.getAttribute('href')).toBe(ST_DONGGUAN_CSS)
    expect(kinghwa.crossOrigin).toBe('anonymous')
    expect(dongguan.crossOrigin).toBe('anonymous')
  })

  it('stores zen prefs per user and refuses anonymous writes', () => {
    saveZenPrefs('', { ...ZEN_PREFS_DEFAULT, wide: true, theme: 'night' })
    expect(localStorage.length).toBe(0)
    expect(loadZenPrefs('')).toEqual(ZEN_PREFS_DEFAULT)

    saveZenPrefs('u1', { size: 20, wide: true, font: 'kai', theme: 'night' })
    saveZenPrefs('u2', { size: 14, wide: false, font: 'hei', theme: 'eye' })
    saveZenPrefs('u3', { size: 16, wide: true, font: 'kinghwa', theme: 'normal' })
    expect(loadZenPrefs('u1')).toEqual({ size: 20, wide: true, font: 'kai', theme: 'night' })
    expect(loadZenPrefs('u2')).toEqual({ size: 14, wide: false, font: 'hei', theme: 'eye' })
    expect(loadZenPrefs('u3')).toEqual({ size: 16, wide: true, font: 'kinghwa', theme: 'normal' })
    expect(localStorage.getItem(zenPrefsStorageKey('u1'))).toContain('"font":"kai"')
    expect(localStorage.getItem(zenPrefsStorageKey('u3'))).toContain('"font":"kinghwa"')
  })

  it('reads the logged-in user id from session storage', () => {
    expect(readSessionUserId()).toBe('')
    sessionStorage.setItem('ontotrace.me', JSON.stringify({ id: 'user-9', username: 'hanbd' }))
    expect(readSessionUserId()).toBe('user-9')
  })
})
