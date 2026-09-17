<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { startJob } from './jobs'
import type { OutlineResponse } from '@/features/reader/types'

const props = defineProps<{
  open: boolean
  versionId: string
  pathPrefix?: string
  unitCount?: number
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  started: [jobId: string]
  error: [message: string]
}>()

const confirmFull = ref(false)
const loading = ref(false)
const error = ref('')
const budget = ref(0)

watch(
  () => [props.open, props.versionId, props.pathPrefix, props.unitCount] as const,
  async ([open, versionId, pathPrefix, unitCount]) => {
    if (!open || !versionId) {
      return
    }
    error.value = ''
    confirmFull.value = false
    if (typeof unitCount === 'number' && unitCount > 0 && pathPrefix) {
      budget.value = unitCount
      return
    }
    try {
      const outline = await api<OutlineResponse>(`/api/document-versions/${versionId}/outline`)
      budget.value = outline.unitCount
    } catch {
      budget.value = unitCount ?? 0
    }
  },
)

async function confirm() {
  if (!props.pathPrefix && !confirmFull.value) {
    error.value = '全书抽取需要选到卷或更细，或勾选全文并查看预算'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const body = props.pathPrefix
      ? { pathPrefix: props.pathPrefix }
      : { confirmFullDocument: true }
    const job = await startJob(`/api/document-versions/${props.versionId}/edu-jobs`, body)
    emit('started', job.jobId)
    emit('update:open', false)
  } catch (err) {
    const message = err instanceof ApiError ? err.message : '抽取失败'
    error.value = message
    emit('error', message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>抽取 EDU</DialogTitle>
        <DialogDescription>
          {{ pathPrefix ? `将抽取「${pathPrefix}」下的 ${budget} 段。` : `未选卷时默认不能全文抽取。当前版本共 ${budget} 段。` }}
        </DialogDescription>
      </DialogHeader>
      <label v-if="!pathPrefix" class="flex items-center gap-2 text-sm">
        <input v-model="confirmFull" type="checkbox" />
        <span>确认抽取全文（{{ budget }} 段）</span>
      </label>
      <p v-if="error" class="text-sm text-destructive">{{ error }}</p>
      <DialogFooter>
        <Button type="button" variant="outline" @click="emit('update:open', false)">取消</Button>
        <Button type="button" :disabled="loading" @click="confirm">开始抽取</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
