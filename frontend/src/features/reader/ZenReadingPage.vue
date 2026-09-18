<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { PanelLeftIcon } from '@lucide/vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Slider } from '@/components/ui/slider'
import { TooltipProvider } from '@/components/ui/tooltip'
import OutlineTree from './OutlineTree.vue'
import { firstLeaf, type OutlineResponse, type TextUnit } from './types'
import {
  ensureNotoSerifCjk,
  parseZenSize,
  parseZenWide,
  ZEN_SIZE_MAX,
  ZEN_SIZE_MIN,
  ZEN_SIZE_PRESETS,
} from './zen'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => String(route.params.documentId))
const versionId = computed(() => String(route.params.versionId))
const selectedPath = computed(() => (typeof route.query.path === 'string' ? route.query.path : ''))
const fontSize = computed(() => parseZenSize(route.query.size))
const fullWidth = computed(() => parseZenWide(route.query.wide))
const outlineOpen = ref(false)

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

function replaceQuery(next: { path?: string; size?: number; wide?: boolean }) {
  const path = next.path ?? selectedPath.value
  const size = parseZenSize(next.size ?? fontSize.value)
  const wide = next.wide ?? fullWidth.value
  const query: Record<string, string> = { size: String(size) }
  if (path) {
    query.path = path
  }
  if (wide) {
    query.wide = '1'
  }
  if (
    route.query.path === query.path &&
    route.query.size === query.size &&
    (route.query.wide === '1') === wide
  ) {
    return
  }
  void router.replace({ query })
}

watch(
  () => outline.data.value,
  (data) => {
    if (!data?.nodes.length) {
      return
    }
    if (!selectedPath.value) {
      const path = firstLeaf(data.nodes)
      if (path) {
        replaceQuery({ path })
      }
    }
  },
  { immediate: true },
)

watch(
  () => route.query.size,
  (raw) => {
    const parsed = parseZenSize(raw)
    if (String(parsed) !== raw) {
      replaceQuery({ size: parsed })
    }
  },
  { immediate: true },
)

onMounted(() => {
  ensureNotoSerifCjk()
})

function selectPath(path: string) {
  outlineOpen.value = false
  replaceQuery({ path })
}

function setSize(size: number) {
  replaceQuery({ size })
}

function toggleFullWidth() {
  replaceQuery({ wide: !fullWidth.value })
}

function onSlider(values: number[] | undefined) {
  const next = values?.[0]
  if (next == null) {
    return
  }
  setSize(next)
}

function exitZen() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  void router.replace(`/documents/${documentId.value}`)
}
</script>

<template>
  <TooltipProvider>
    <div
      class="zen-page flex h-dvh min-h-0 flex-col overflow-hidden"
      :style="{ '--zen-size': `${fontSize}px` }"
      data-reader-font="noto"
    >
      <header class="flex shrink-0 flex-wrap items-center gap-2 px-3 py-2">
        <Button size="sm" type="button" variant="ghost" @click="exitZen">退出</Button>
        <Button class="md:hidden" size="sm" type="button" variant="ghost" @click="outlineOpen = !outlineOpen">
          <PanelLeftIcon />
          目录
        </Button>
        <div class="ml-auto flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            type="button"
            :variant="fullWidth ? 'secondary' : 'ghost'"
            :aria-pressed="fullWidth"
            aria-label="全宽"
            @click="toggleFullWidth"
          >
            全宽
          </Button>
          <Button
            size="sm"
            type="button"
            :variant="fontSize === ZEN_SIZE_PRESETS.small ? 'secondary' : 'ghost'"
            :aria-pressed="fontSize === ZEN_SIZE_PRESETS.small"
            @click="setSize(ZEN_SIZE_PRESETS.small)"
          >
            小
          </Button>
          <Button
            size="sm"
            type="button"
            :variant="fontSize === ZEN_SIZE_PRESETS.medium ? 'secondary' : 'ghost'"
            :aria-pressed="fontSize === ZEN_SIZE_PRESETS.medium"
            @click="setSize(ZEN_SIZE_PRESETS.medium)"
          >
            中
          </Button>
          <Button
            size="sm"
            type="button"
            :variant="fontSize === ZEN_SIZE_PRESETS.large ? 'secondary' : 'ghost'"
            :aria-pressed="fontSize === ZEN_SIZE_PRESETS.large"
            @click="setSize(ZEN_SIZE_PRESETS.large)"
          >
            大
          </Button>
          <Slider
            class="w-32"
            :model-value="[fontSize]"
            :min="ZEN_SIZE_MIN"
            :max="ZEN_SIZE_MAX"
            :step="1"
            aria-label="正文字号"
            @update:model-value="onSlider"
          />
        </div>
      </header>
      <div class="flex min-h-0 flex-1">
        <aside
          class="zen-outline h-full min-h-0 w-64 shrink-0 flex-col border-r border-black/10"
          :class="outlineOpen ? 'flex' : 'hidden md:flex'"
        >
          <ScrollArea class="h-full px-2 py-3">
            <OutlineTree
              v-if="outline.data.value?.nodes.length"
              :nodes="outline.data.value.nodes"
              :selected-path="selectedPath"
              @select="selectPath"
            />
            <p v-else class="px-2 text-sm" style="color: #555">还没有目录。</p>
          </ScrollArea>
        </aside>
        <ScrollArea class="min-w-0 flex-1">
          <div
            class="zen-body px-6 py-8"
            :class="fullWidth ? 'w-full' : 'mx-auto max-w-[42em]'"
            :data-zen-wide="fullWidth ? '1' : '0'"
          >
            <p v-for="unit in units.data.value ?? []" :key="unit.id" class="zen-classical">
              {{ unit.displayText }}
            </p>
            <p v-if="selectedPath && !(units.data.value ?? []).length && !units.isPending.value" class="text-sm" style="color: #555">
              这一节点下没有段落。
            </p>
          </div>
        </ScrollArea>
      </div>
    </div>
  </TooltipProvider>
</template>

<style scoped>
.zen-page {
  --zen-bg: #f6f1e7;
  --zen-fg: #222;
  --zen-muted: #555;
  --font-family-classical: "Noto Serif CJK", "Songti SC", "STSong", "SimSun", serif;
  --muted-foreground: var(--zen-muted);
  --foreground: var(--zen-fg);
  --muted: rgb(34 34 34 / 8%);
  background: var(--zen-bg);
  color: var(--zen-fg);
}

.zen-classical {
  font-family: var(--font-family-classical);
  font-size: calc(var(--zen-size, 16px) + 2px);
  line-height: 2;
  letter-spacing: 0.03em;
  color: var(--zen-fg);
  text-indent: 0;
  margin: 0 0 0.875em;
}
</style>
