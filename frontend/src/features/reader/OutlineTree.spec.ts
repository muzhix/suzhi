import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import OutlineTree from './OutlineTree.vue'
import type { OutlineNode } from './types'

const nodes: OutlineNode[] = [
  {
    path: '卷第十八（汉纪十）',
    label: '卷第十八（汉纪十）',
    unitCount: 3,
    children: [
      {
        path: '卷第十八（汉纪十）/世宗孝武皇帝上之下',
        label: '世宗孝武皇帝上之下',
        unitCount: 3,
        children: [
          {
            path: '卷第十八（汉纪十）/世宗孝武皇帝上之下/元光二年',
            label: '元光二年',
            unitCount: 1,
            children: [],
          },
        ],
      },
    ],
  },
]

describe('OutlineTree fold', () => {
  it('collapses an expanded parent on click and expands it again', async () => {
    const wrapper = mount(OutlineTree, {
      props: {
        nodes,
        selectedPath: '卷第十八（汉纪十）/世宗孝武皇帝上之下/元光二年',
      },
    })
    expect(wrapper.text()).toContain('元光二年')
    const juan = wrapper.get('button')
    expect(juan.attributes('aria-expanded')).toBe('true')
    await juan.trigger('click')
    expect(wrapper.text()).not.toContain('元光二年')
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('false')
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('元光二年')
    expect(wrapper.get('button').attributes('aria-expanded')).toBe('true')
  })
})
