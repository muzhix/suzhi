<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { PanelLeftIcon } from '@lucide/vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import DocumentJobBadge from '@/features/documents/DocumentJobBadge.vue'
import ExtractEduDialog from '@/features/documents/ExtractEduDialog.vue'
import OutlineTree from './OutlineTree.vue'
import { firstLeaf, type EduItem, type OutlineResponse, type TextUnit } from './types'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const versionId = computed(() => String(route.params.versionId))
const selectedPath = computed(() => (typeof route.query.path === 'string' ? route.query.path : ''))
const selectedUnitId = computed(() => (typeof route.query.unit === 'string' ? route.query.unit : ''))
const mobilePanel = ref<'text' | 'edu'>('text')
const outlineOpen = ref(false)
const eduDialogOpen = ref(false)
const jobId = ref<string | null>(null)
const extracting = ref(false)
const actionError = ref('')
const highlighted = ref<string[]>([])

const outline = useQuery({
  queryKey: computed(() => ['outline', versionId.value]),
  queryFn: () => api<OutlineResponse>(`/api/document-versions/${versionId.value}/outline`),
  enabled: computed(() => Boolean(versionId.value)),
})

const units = useQuery({
  queryKey: computed(() => ['text-units', versionId.value, selectedPath.value]),
  queryFn: () => {
    const params = new URLSearchParams()
    if (selectedPath.value) {
      params.set('pathPrefix', selectedPath.value)
    }
    return api<TextUnit[]>(`/api/document-versions/${versionId.value}/text-units?${params}`)
  },
  enabled: computed(() => Boolean(versionId.value && selectedPath.value)),
})

const edus = useQuery({
  queryKey: computed(() => ['edus', versionId.value]),
  queryFn: () => api<EduItem[]>(`/api/document-versions/${versionId.value}/edus`),
  enabled: computed(() => Boolean(versionId.value)),
})

const selectedEdus = computed(() => {
  if (!selectedUnitId.value) {
    return []
  }
  return edus.data.value?.filter((edu) => edu.sources.some((source) => source.textUnitId === selectedUnitId.value)) ?? []
})

watch(
  () => outline.data.value,
  (data) => {
    if (!data?.nodes.length) {
      return
    }
    if (!selectedPath.value) {
      const path = firstLeaf(data.nodes)
      if (path) {
        void router.replace({ query: { ...route.query, path } })
      }
    }
  },
  { immediate: true },
)

watch(
  () => units.data.value,
  (items) => {
    if (!items?.length) {
      return
    }
    if (!selectedUnitId.value || !items.some((unit) => unit.id === selectedUnitId.value)) {
      void router.replace({ query: { ...route.query, path: selectedPath.value, unit: items[0].id } })
    }
  },
)

function selectPath(path: string) {
  outlineOpen.value = false
  highlighted.value = []
  void router.replace({ query: { path } })
}

function selectUnit(id: string) {
  highlighted.value = [id]
  mobilePanel.value = 'text'
  void router.replace({ query: { ...route.query, path: selectedPath.value, unit: id } })
}

function selectEdu(edu: EduItem) {
  highlighted.value = edu.sources.map((source) => source.textUnitId)
  const primary = edu.sources[0]?.textUnitId
  if (primary) {
    void router.replace({ query: { ...route.query, unit: primary } })
  }
}

function unitClass(unit: TextUnit) {
  const on = unit.id === selectedUnitId.value || highlighted.value.includes(unit.id)
  return on
    ? 'reader-text reader-highlight cursor-pointer rounded px-2 py-1'
    : 'reader-text hover:bg-muted/60 cursor-pointer rounded px-2 py-1'
}

function precisionLabel(precision: string) {
  if (precision === 'exact') {
    return ''
  }
  if (precision === 'page') {
    return '页面级定位'
  }
  return '文本单元级定位'
}

async function onJobDone() {
  extracting.value = false
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['edus', versionId.value] }),
    queryClient.invalidateQueries({ queryKey: ['outline', versionId.value] }),
  ])
}
</script>

<template>
  <AppLayout>
    <div class="space-y-3">
      <div class="flex flex-wrap items-center justify-end gap-2">
        <Button class="lg:hidden" size="sm" type="button" variant="outline" @click="outlineOpen = true">
          <PanelLeftIcon class="size-4" />
          目录
        </Button>
        <div class="lg:hidden flex gap-1">
          <Button size="sm" type="button" :variant="mobilePanel === 'text' ? 'default' : 'outline'" @click="mobilePanel = 'text'">
            原文
          </Button>
          <Button size="sm" type="button" :variant="mobilePanel === 'edu' ? 'default' : 'outline'" @click="mobilePanel = 'edu'">
            EDU
          </Button>
        </div>
        <Button
          size="sm"
          type="button"
          :disabled="!selectedPath || extracting"
          @click="eduDialogOpen = true"
        >
          前往抽取
        </Button>
        <DocumentJobBadge v-if="jobId" :job-id="jobId" @done="onJobDone" />
      </div>
      <p v-if="actionError" class="text-sm text-destructive">{{ actionError }}</p>
      <div class="grid min-h-[70vh] grid-cols-1 gap-3 lg:grid-cols-[240px_minmax(0,1fr)_320px]">
        <Card class="hidden lg:flex">
          <CardContent class="p-3">
            <ScrollArea class="h-[70vh] pr-2">
              <OutlineTree
                v-if="outline.data.value?.nodes.length"
                :nodes="outline.data.value.nodes"
                :selected-path="selectedPath"
                @select="selectPath"
              />
              <p v-else class="text-sm text-muted-foreground">还没有目录。</p>
            </ScrollArea>
          </CardContent>
        </Card>
        <Card :class="mobilePanel === 'text' ? '' : 'hidden lg:flex'">
          <CardContent class="p-3">
            <ScrollArea class="h-[70vh] pr-2">
              <p
                v-for="unit in units.data.value ?? []"
                :id="`unit-${unit.id}`"
                :key="unit.id"
                :class="unitClass(unit)"
                :aria-current="unit.id === selectedUnitId ? 'true' : undefined"
                @click="selectUnit(unit.id)"
              >
                {{ unit.displayText }}
              </p>
              <p v-if="selectedPath && !(units.data.value ?? []).length" class="text-sm text-muted-foreground">
                这一节点下没有段落。
              </p>
            </ScrollArea>
          </CardContent>
        </Card>
        <div :class="mobilePanel === 'edu' ? 'space-y-3' : 'hidden space-y-3 lg:block'">
          <p v-if="!selectedUnitId" class="text-sm text-muted-foreground">先选择一段原文。</p>
          <p v-else-if="!selectedEdus.length" class="text-sm text-muted-foreground">
            这一段还没有 EDU。
            <Button class="ml-1" size="sm" variant="link" type="button" @click="eduDialogOpen = true">前往抽取</Button>
          </p>
          <Card v-for="edu in selectedEdus" :key="edu.id">
            <CardContent class="space-y-2 p-4">
              <button class="text-left text-sm" type="button" @click="selectEdu(edu)">
                {{ edu.text }}
              </button>
              <Badge variant="secondary">{{ edu.status }}</Badge>
              <p v-if="precisionLabel(edu.locationPrecision)" class="text-xs text-muted-foreground">
                {{ precisionLabel(edu.locationPrecision) }}
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
    <Sheet :open="outlineOpen" @update:open="outlineOpen = $event">
      <SheetContent side="left" class="w-80">
        <SheetHeader>
          <SheetTitle>目录</SheetTitle>
        </SheetHeader>
        <ScrollArea class="h-[80vh] pr-2">
          <OutlineTree
            v-if="outline.data.value?.nodes.length"
            :nodes="outline.data.value.nodes"
            :selected-path="selectedPath"
            @select="selectPath"
          />
        </ScrollArea>
      </SheetContent>
    </Sheet>
    <ExtractEduDialog
      :open="eduDialogOpen"
      :version-id="versionId"
      :path-prefix="selectedPath"
      :unit-count="units.data.value?.length ?? 0"
      @update:open="eduDialogOpen = $event"
      @started="
        (id) => {
          jobId = id
          extracting = true
          actionError = ''
        }
      "
      @error="
        (message) => {
          actionError = message
          extracting = false
        }
      "
    />
  </AppLayout>
</template>
