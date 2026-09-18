import { afterEach, describe, expect, it } from 'vitest'
import {
  ensureNotoSerifCjk,
  NOTO_SERIF_CJK_CSS,
  parseZenSize,
  zenBodyFontSize,
  zenReadingLocation,
  ZEN_SIZE_DEFAULT,
  ZEN_SIZE_PRESETS,
} from './zen'

describe('zen reading helpers', () => {
  afterEach(() => {
    document.getElementById('noto-serif-cjk-font-css')?.remove()
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

  it('builds the shell-less zen path without size', () => {
    expect(zenReadingLocation('d1', 'v1')).toEqual({
      path: '/documents/d1/versions/v1/zen',
      query: {},
    })
    expect(zenReadingLocation('d1', 'v1', '本纪/卷一')).toEqual({
      path: '/documents/d1/versions/v1/zen',
      query: { path: '本纪/卷一' },
    })
  })

  it('injects the Noto Serif CJK stylesheet once', () => {
    ensureNotoSerifCjk()
    ensureNotoSerifCjk()
    const links = document.querySelectorAll('#noto-serif-cjk-font-css')
    expect(links).toHaveLength(1)
    const link = links[0] as HTMLLinkElement
    expect(link.rel).toBe('stylesheet')
    expect(link.getAttribute('href')).toBe(NOTO_SERIF_CJK_CSS)
    expect(link.crossOrigin).toBe('anonymous')
  })
})
