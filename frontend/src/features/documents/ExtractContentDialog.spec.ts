import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Select } from '@/components/ui/select'
import { NONE_STRUCTURE_SCHEME } from '@/features/reader/types'
import ExtractContentDialog from './ExtractContentDialog.vue'

const api = vi.hoisted(() => vi.fn())
const startJob = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', () => ({
  api: (...args: unknown[]) => api(...args),
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

vi.mock('./jobs', () => ({
  startJob: (...args: unknown[]) => startJob(...args),
}))

const scheme = {
  id: 'jizhuan-toc-divergent',
  name: '纪传体·目录异形',
  profile: { id: 'jizhuan-toc-divergent', name: '纪传体·目录异形', headings: [] },
}

const previewOk = {
  acceptable: true,
  unitCount: 2,
  headingCount: 3,
  tocLineCount: 3,
  unmatchedHeadings: [],
  unmatchedVolumes: [],
  warnings: [],
  outline: [{ path: '本纪/卷一/高祖', label: '高祖', unitCount: 2, children: [] }],
  units: [
    { path: '本纪/卷一/高祖', text: '高祖神尧' },
    { path: '本纪/卷一/高祖', text: '二年春正月' },
  ],
}

function confirmButton() {
  return Array.from(document.querySelectorAll('button')).find((button) => button.textContent?.trim() === '确认提取')
}

describe('ExtractContentDialog', () => {
  beforeEach(() => {
    api.mockReset()
    startJob.mockReset()
    api.mockImplementation(async (path: string) => {
      if (path === '/api/structure-schemes') {
        return [scheme]
      }
      if (String(path).includes('extraction-previews')) {
        return previewOk
      }
      throw new Error(String(path))
    })
    startJob.mockResolvedValue({ jobId: 'job-1' })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('previews when a scheme is selected and gates confirm on acceptable', async () => {
    const wrapper = mount(ExtractContentDialog, {
      props: { open: false, documentId: 'doc-1' },
      attachTo: document.body,
    })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(api).toHaveBeenCalledWith('/api/structure-schemes')
    expect(api.mock.calls.some((call) => String(call[0]).includes('extraction-previews'))).toBe(false)
    expect(confirmButton()?.disabled).toBe(false)

    await wrapper.findComponent(Select).vm.$emit('update:modelValue', 'jizhuan-toc-divergent')
    await flushPromises()
    expect(api).toHaveBeenCalledWith(
      '/api/documents/doc-1/extraction-previews',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(document.body.textContent).toContain('高祖神尧')
    expect(confirmButton()?.disabled).toBe(false)

    api.mockImplementation(async (path: string) => {
      if (path === '/api/structure-schemes') {
        return [scheme]
      }
      if (String(path).includes('extraction-previews')) {
        return { ...previewOk, acceptable: false, units: [], outline: [] }
      }
      throw new Error(String(path))
    })
    await wrapper.findComponent(Select).vm.$emit('update:modelValue', NONE_STRUCTURE_SCHEME)
    await wrapper.findComponent(Select).vm.$emit('update:modelValue', 'jizhuan-toc-divergent')
    await flushPromises()
    expect(confirmButton()?.disabled).toBe(true)
    wrapper.unmount()
  })
})
