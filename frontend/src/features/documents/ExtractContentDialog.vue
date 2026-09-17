<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { startJob } from './jobs'
import type { PreviewResponse, StructureProfile, StructureScheme } from '@/features/reader/types'

const props = defineProps<{
  open: boolean
  documentId: string
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  started: [jobId: string]
  error: [message: string]
}>()

const schemes = ref<StructureScheme[]>([])
const schemeId = ref('')
const profile = ref<StructureProfile | null>(null)
const preview = ref<PreviewResponse | null>(null)
const loading = ref(false)
const previewing = ref(false)
const error = ref('')

const selectClass =
  'border-input h-8 w-full rounded-lg border bg-transparent px-2.5 text-sm outline-none'

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    error.value = ''
    preview.value = null
    schemeId.value = ''
    profile.value = null
    if (!schemes.value.length) {
      try {
        schemes.value = await api<StructureScheme[]>('/api/structure-schemes')
      } catch (err) {
        error.value = err instanceof ApiError ? err.message : '无法加载结构方案'
      }
    }
  },
)

function onSchemeChange(id: string) {
  schemeId.value = id
  preview.value = null
  const found = schemes.value.find((item) => item.id === id)
  profile.value = found ? structuredClone(found.profile) : null
}

async function runPreview() {
  if (!schemeId.value || !profile.value) {
    return
  }
  previewing.value = true
  error.value = ''
  try {
    preview.value = await api<PreviewResponse>(`/api/documents/${props.documentId}/extraction-previews`, {
      method: 'POST',
      body: JSON.stringify({ schemeId: schemeId.value, profile: profile.value }),
    })
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '预览失败'
  } finally {
    previewing.value = false
  }
}

async function confirm() {
  loading.value = true
  error.value = ''
  try {
    const body = schemeId.value && profile.value ? { schemeId: schemeId.value, profile: profile.value } : undefined
    if (schemeId.value && !preview.value?.acceptable) {
      error.value = '请先预览并确认结构识别可用'
      return
    }
    const job = await startJob(`/api/documents/${props.documentId}/extraction-jobs`, body)
    emit('started', job.jobId)
    emit('update:open', false)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '提取失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-w-xl">
      <DialogHeader>
        <DialogTitle>提取内容</DialogTitle>
        <DialogDescription>短文本可不选方案，空行切段。全书先选体例、预览目录，再确认。</DialogDescription>
      </DialogHeader>
      <div class="grid gap-3">
        <div class="grid gap-2">
          <Label for="scheme">结构方案</Label>
          <select id="scheme" :class="selectClass" :value="schemeId" @change="onSchemeChange(($event.target as HTMLSelectElement).value)">
            <option value="">无结构方案（空行切段）</option>
            <option v-for="item in schemes" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </div>
        <template v-if="profile">
          <div class="grid gap-2">
            <Label for="paragraph">切段</Label>
            <select id="paragraph" :class="selectClass" v-model="profile.paragraph">
              <option value="blank">空行</option>
              <option value="indent">全角缩进</option>
              <option value="blank_or_indent">空行或全角缩进</option>
            </select>
          </div>
          <div v-for="(heading, index) in profile.headings" :key="heading.id + index" class="grid gap-2">
            <Label :for="`heading-${index}`">标题规则 {{ heading.id }}</Label>
            <Input :id="`heading-${index}`" v-model="heading.pattern" />
          </div>
          <Button type="button" variant="outline" :disabled="previewing" @click="runPreview">预览目录</Button>
          <div v-if="preview" class="space-y-2 text-sm">
            <p>
              标题 {{ preview.headingCount }} · 段落 {{ preview.unitCount }}
              <span :class="preview.acceptable ? 'text-muted-foreground' : 'text-destructive'">
                · {{ preview.acceptable ? '可以确认' : '识别过差，请改规则' }}
              </span>
            </p>
            <p v-if="preview.unmatchedVolumes.length" class="text-destructive">
              未对照卷：{{ preview.unmatchedVolumes.slice(0, 8).join('；') }}
            </p>
            <p v-if="preview.unmatchedHeadings.length" class="text-muted-foreground">
              未识别标题：{{ preview.unmatchedHeadings.slice(0, 8).join('；') }}
            </p>
            <ul class="max-h-40 overflow-auto text-muted-foreground">
              <li v-for="node in preview.outline" :key="node.path">
                {{ node.label }} · {{ node.unitCount }}
              </li>
            </ul>
          </div>
        </template>
        <p v-if="error" class="text-sm text-destructive">{{ error }}</p>
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" @click="emit('update:open', false)">取消</Button>
        <Button type="button" :disabled="loading || Boolean(schemeId && !preview?.acceptable)" @click="confirm">
          确认提取
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
