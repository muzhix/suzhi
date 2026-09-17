<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import OutlineTree from '@/features/reader/OutlineTree.vue'
import {
  canConfirmExtract,
  firstLeaf,
  NONE_STRUCTURE_SCHEME,
  schemeIdFromSelect,
  unitsForPath,
  type PreviewResponse,
  type StructureProfile,
  type StructureScheme,
} from '@/features/reader/types'
import { startJob } from './jobs'

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
const selectedPath = ref('')
const loading = ref(false)
const previewing = ref(false)
const error = ref('')
let previewSeq = 0

const currentUnits = computed(() => unitsForPath(preview.value?.units ?? [], selectedPath.value))
const confirmDisabled = computed(
  () => loading.value || previewing.value || !canConfirmExtract(schemeId.value, preview.value),
)

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      previewSeq += 1
      previewing.value = false
      return
    }
    error.value = ''
    preview.value = null
    selectedPath.value = ''
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
  { immediate: true },
)

function onSchemeChange(value: unknown) {
  const id = schemeIdFromSelect(value)
  schemeId.value = id
  preview.value = null
  selectedPath.value = ''
  error.value = ''
  const found = schemes.value.find((item) => item.id === id)
  profile.value = found?.profile ?? null
  if (id && profile.value) {
    void runPreview()
    return
  }
  previewSeq += 1
  previewing.value = false
}

async function runPreview() {
  const id = schemeId.value
  const currentProfile = profile.value
  if (!id || !currentProfile) {
    return
  }
  const seq = ++previewSeq
  previewing.value = true
  error.value = ''
  preview.value = null
  selectedPath.value = ''
  try {
    const result = await api<PreviewResponse>(`/api/documents/${props.documentId}/extraction-previews`, {
      method: 'POST',
      body: JSON.stringify({ schemeId: id, profile: currentProfile }),
    })
    if (seq !== previewSeq) {
      return
    }
    preview.value = result
    selectedPath.value = firstLeaf(result.outline) ?? ''
  } catch (err) {
    if (seq !== previewSeq) {
      return
    }
    error.value = err instanceof ApiError ? err.message : '预览失败'
  } finally {
    if (seq === previewSeq) {
      previewing.value = false
    }
  }
}

async function confirm() {
  if (!canConfirmExtract(schemeId.value, preview.value)) {
    error.value = '请先预览并确认结构识别可用'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const body = schemeId.value && profile.value ? { schemeId: schemeId.value, profile: profile.value } : undefined
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
    <DialogContent class="flex h-[min(90vh,52rem)] w-[min(96vw,72rem)] max-w-none flex-col gap-4 sm:max-w-none">
      <DialogHeader class="shrink-0 pr-8">
        <DialogTitle>提取内容</DialogTitle>
        <DialogDescription>短文本可不选方案，空行切段。选体例后预览目录，确认才写版本。</DialogDescription>
      </DialogHeader>
      <div class="grid shrink-0 gap-2">
        <Label for="scheme">结构方案</Label>
        <Select :model-value="schemeId || NONE_STRUCTURE_SCHEME" @update:model-value="onSchemeChange">
          <SelectTrigger id="scheme" class="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent position="popper" class="z-[80]">
            <SelectItem :value="NONE_STRUCTURE_SCHEME">不选方案（空行切段）</SelectItem>
            <SelectItem v-for="item in schemes" :key="item.id" :value="item.id">{{ item.name }}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <p v-if="preview" class="shrink-0 text-sm">
        标题 {{ preview.headingCount }} · 段落 {{ preview.unitCount }}
        <span :class="preview.acceptable ? 'text-muted-foreground' : 'text-destructive'">
          · {{ preview.acceptable ? '可以确认' : '识别过差，请换方案' }}
        </span>
      </p>
      <p v-if="preview?.unmatchedVolumes.length" class="shrink-0 text-sm text-destructive">
        未对照卷：{{ preview.unmatchedVolumes.slice(0, 8).join('；') }}
      </p>
      <p v-if="preview?.unmatchedHeadings.length" class="shrink-0 text-sm text-muted-foreground">
        未识别标题：{{ preview.unmatchedHeadings.slice(0, 8).join('；') }}
      </p>
      <div class="grid min-h-0 flex-1 grid-cols-1 gap-3 md:grid-cols-[16rem_minmax(0,1fr)]">
        <ScrollArea class="min-h-48 rounded-lg border md:min-h-0">
          <div class="p-2">
            <p v-if="!schemeId" class="px-2 py-1 text-sm text-muted-foreground">不选方案时没有目录树，确认后按空行切段。</p>
            <p v-else-if="previewing" class="px-2 py-1 text-sm text-muted-foreground">正在预览…</p>
            <OutlineTree
              v-else-if="preview?.outline.length"
              :nodes="preview.outline"
              :selected-path="selectedPath"
              @select="selectedPath = $event"
            />
            <p v-else class="px-2 py-1 text-sm text-muted-foreground">没有识别出目录。</p>
          </div>
        </ScrollArea>
        <ScrollArea class="min-h-48 rounded-lg border md:min-h-0">
          <div class="space-y-2 p-3">
            <p v-if="!schemeId" class="text-sm text-muted-foreground">确认后写入 p1、p2。</p>
            <p v-else-if="previewing" class="text-sm text-muted-foreground">正在预览…</p>
            <p v-for="(unit, index) in currentUnits" :key="`${unit.path}-${index}`" class="text-sm leading-relaxed">
              {{ unit.text }}
            </p>
            <p v-if="schemeId && !previewing && selectedPath && !currentUnits.length" class="text-sm text-muted-foreground">
              这一节点下没有段落。
            </p>
          </div>
        </ScrollArea>
      </div>
      <p v-if="error" class="shrink-0 text-sm text-destructive">{{ error }}</p>
      <DialogFooter class="shrink-0">
        <Button type="button" variant="outline" @click="emit('update:open', false)">取消</Button>
        <Button type="button" :disabled="confirmDisabled" @click="confirm">确认提取</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
