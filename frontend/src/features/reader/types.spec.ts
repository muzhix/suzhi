import { describe, expect, it } from 'vitest'
import {
  canConfirmExtract,
  firstLeaf,
  firstLeafOf,
  NONE_STRUCTURE_SCHEME,
  schemeIdFromSelect,
  unitsForPath,
  type OutlineNode,
} from './types'

describe('outline helpers', () => {
  it('picks the first leaf for the default reader path', () => {
    const nodes: OutlineNode[] = [
      {
        path: '本纪',
        label: '本纪',
        unitCount: 4,
        children: [
          {
            path: '本纪/卷一',
            label: '卷一',
            unitCount: 2,
            children: [{ path: '本纪/卷一/高祖', label: '高祖', unitCount: 2, children: [] }],
          },
        ],
      },
    ]
    expect(firstLeaf(nodes)).toBe('本纪/卷一/高祖')
    expect(firstLeafOf(nodes[0])).toBe('本纪/卷一/高祖')
  })
})

describe('guided extract helpers', () => {
  it('maps the empty Select row to no scheme', () => {
    expect(schemeIdFromSelect(NONE_STRUCTURE_SCHEME)).toBe('')
    expect(schemeIdFromSelect('')).toBe('')
    expect(schemeIdFromSelect('jizhuan-toc-divergent')).toBe('jizhuan-toc-divergent')
  })

  it('allows confirm without a scheme, and only after acceptable preview with a scheme', () => {
    expect(canConfirmExtract('', null)).toBe(true)
    expect(canConfirmExtract('jizhuan-toc-divergent', null)).toBe(false)
    expect(canConfirmExtract('jizhuan-toc-divergent', { acceptable: false })).toBe(false)
    expect(canConfirmExtract('jizhuan-toc-divergent', { acceptable: true })).toBe(true)
  })

  it('filters preview units to the selected path', () => {
    const units = [
      { path: '本纪/卷一/高祖', text: '高祖神尧' },
      { path: '本纪/卷一/高祖', text: '二年春正月' },
      { path: '本纪/卷二/太宗', text: '太宗讳世民' },
    ]
    expect(unitsForPath(units, '本纪/卷一/高祖').map((unit) => unit.text)).toEqual(['高祖神尧', '二年春正月'])
    expect(unitsForPath(units, '本纪').map((unit) => unit.text)).toHaveLength(3)
    expect(unitsForPath(units, '')).toEqual([])
  })
})
