import { afterEach, describe, expect, it } from 'vitest'
import {
  ensureNotoSerifCjk,
  ensureZenFont,
  loadZenPrefs,
  LXGW_WENKAI_MONO_CSS,
  NOTO_SERIF_CJK_CSS,
  parseZenFont,
  parseZenSize,
  parseZenTheme,
  parseZenWide,
  readSessionUserId,
  saveZenPrefs,
  zenBodyFontSize,
  zenPrefsStorageKey,
  zenReadingLocation,
  ZEN_PREFS_DEFAULT,
  ZEN_SIZE_DEFAULT,
  ZEN_SIZE_PRESETS,
} from './zen'

describe('zen reading helpers', () => {
  afterEach(() => {
    document.getElementById('noto-serif-cjk-font-css')?.remove()
    document.getElementById('lxgw-wenkai-mono-font-css')?.remove()
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
    expect(parseZenFont('kinghwa')).toBe('noto')
    expect(parseZenTheme('eye')).toBe('eye')
    expect(parseZenTheme('night')).toBe('night')
    expect(parseZenTheme('sepia')).toBe('normal')
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

  it('injects song and kai stylesheets once and skips hei', () => {
    ensureNotoSerifCjk()
    ensureZenFont('noto')
    ensureZenFont('kai')
    ensureZenFont('kai')
    ensureZenFont('hei')
    expect(document.querySelectorAll('#noto-serif-cjk-font-css')).toHaveLength(1)
    expect(document.querySelectorAll('#lxgw-wenkai-mono-font-css')).toHaveLength(1)
    const noto = document.getElementById('noto-serif-cjk-font-css') as HTMLLinkElement
    const kai = document.getElementById('lxgw-wenkai-mono-font-css') as HTMLLinkElement
    expect(noto.getAttribute('href')).toBe(NOTO_SERIF_CJK_CSS)
    expect(kai.getAttribute('href')).toBe(LXGW_WENKAI_MONO_CSS)
    expect(kai.crossOrigin).toBe('anonymous')
  })

  it('stores zen prefs per user and refuses anonymous writes', () => {
    saveZenPrefs('', { ...ZEN_PREFS_DEFAULT, wide: true, theme: 'night' })
    expect(localStorage.length).toBe(0)
    expect(loadZenPrefs('')).toEqual(ZEN_PREFS_DEFAULT)

    saveZenPrefs('u1', { size: 20, wide: true, font: 'kai', theme: 'night' })
    saveZenPrefs('u2', { size: 14, wide: false, font: 'hei', theme: 'eye' })
    expect(loadZenPrefs('u1')).toEqual({ size: 20, wide: true, font: 'kai', theme: 'night' })
    expect(loadZenPrefs('u2')).toEqual({ size: 14, wide: false, font: 'hei', theme: 'eye' })
    expect(localStorage.getItem(zenPrefsStorageKey('u1'))).toContain('"font":"kai"')
  })

  it('reads the logged-in user id from session storage', () => {
    expect(readSessionUserId()).toBe('')
    sessionStorage.setItem('ontotrace.me', JSON.stringify({ id: 'user-9', username: 'hanbd' }))
    expect(readSessionUserId()).toBe('user-9')
  })
})
