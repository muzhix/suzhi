import { describe, expect, it } from 'vitest'
import { firstLeaf, firstLeafOf, type OutlineNode } from './types'

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
