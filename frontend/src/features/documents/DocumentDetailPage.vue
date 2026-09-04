<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import AppLayout from '@/layouts/AppLayout.vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import DocumentJobBadge from './DocumentJobBadge.vue'
import { startJob } from './jobs'

interface DocumentDetail {
  id: string
  title: string
  latestVersionId?: string | null
}

interface TextUnit {
  id: string
  seq: number
  displayText: string
}

interface Edu {
  id: string
  text: string
  status: string
  locationPrecision: string
  sources: { textUnitId: string; quote?: string; precision: string }[]
}

const route = useRoute()
const queryClient = useQueryClient()
const documentId = computed(() => String(route.params.documentId))
const selectedId = ref<string | null>(null)
const jobId = ref<string | null>(null)
const extracting = ref(false)
const actionError = ref('')

const documentQuery = useQuery({
  queryKey: computed(() => ['document', documentId.value]),
  queryFn: () => api<DocumentDetail>(`/api/documents/${documentId.value}`),
})

const versionId = computed(() => documentQuery.data.value?.latestVersionId || '')

const units = useQuery({
  queryKey: computed(() => ['text-units', versionId.value]),
  queryFn: () => api<TextUnit[]>(`/api/document-versions/${versionId.value}/text-units`),
  enabled: computed(() => Boolean(versionId.value)),
})

const edus = useQuery({
  queryKey: computed(() => ['edus', versionId.value]),
  queryFn: () => api<Edu[]>(`/api/document-versions/${versionId.value}/edus`),
  enabled: computed(() => Boolean(versionId.value)),
})

const selectedEdus = computed(
  () => edus.data.value?.filter((edu) => edu.sources.some((source) => source.textUnitId === selectedId.value)) ?? [],
)

watch(
  () => units.data.value,
  (items) => {
    if (!selectedId.value && items?.[0]) {
      selectedId.value = items[0].id
    }
  },
)

function unitClass(unit: TextUnit) {
  return unit.id === selectedId.value
    ? 'reader-text reader-highlight cursor-pointer rounded px-2 py-1'
    : 'reader-text hover:bg-muted/60 cursor-pointer rounded px-2 py-1'
}

async function reextractSelected() {
  if (!versionId.value || !selectedId.value) {
    return
  }
  actionError.value = ''
  extracting.value = true
  try {
    const job = await startJob(`/api/document-versions/${versionId.value}/edu-jobs`, { textUnitId: selectedId.value })
    jobId.value = job.jobId
  } catch (err) {
    extracting.value = false
    actionError.value = err instanceof ApiError ? err.message : '抽取失败'
  }
}

async function onJobDone() {
  extracting.value = false
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['text-units', versionId.value] }),
    queryClient.invalidateQueries({ queryKey: ['edus', versionId.value] }),
    queryClient.invalidateQueries({ queryKey: ['document', documentId.value] }),
  ])
}
</script>

<template>
  <AppLayout>
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-end gap-3">
        <Button size="sm" type="button" :disabled="!selectedId || !versionId || extracting" @click="reextractSelected">
          重新抽取本段
        </Button>
        <DocumentJobBadge v-if="jobId" :job-id="jobId" @done="onJobDone" />
      </div>
      <p v-if="actionError" class="text-sm text-destructive">{{ actionError }}</p>
      <p v-if="!versionId" class="text-sm text-muted-foreground">还没有提取文本。回到文档库上传文件并提取内容。</p>
      <div v-else class="grid min-h-[70vh] grid-cols-1 gap-4 lg:grid-cols-[1fr_360px]">
        <Card>
          <CardHeader>
            <CardTitle>文本单元</CardTitle>
          </CardHeader>
          <CardContent>
            <ScrollArea class="h-[65vh] pr-3">
              <p
                v-for="unit in units.data.value ?? []"
                :id="`unit-${unit.id}`"
                :key="unit.id"
                :class="unitClass(unit)"
                @click="selectedId = unit.id"
              >
                {{ unit.displayText }}
              </p>
            </ScrollArea>
          </CardContent>
        </Card>
        <div class="space-y-3">
          <h2 class="font-medium">本段 EDU</h2>
          <p v-if="!selectedId" class="text-sm text-muted-foreground">先在左侧选择一段文本。</p>
          <p v-else-if="!selectedEdus.length" class="text-sm text-muted-foreground">这一段还没有 EDU。</p>
          <Card v-for="edu in selectedEdus" :key="edu.id">
            <CardContent class="space-y-2 p-4">
              <p class="text-sm">{{ edu.text }}</p>
              <Badge variant="secondary">{{ edu.status }}</Badge>
              <p v-if="edu.locationPrecision !== 'exact'" class="text-xs text-muted-foreground">文本单元级定位</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
