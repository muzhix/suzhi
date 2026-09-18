<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { PanelLeftIcon, SettingsIcon, XIcon } from '@lucide/vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Popover, PopoverClose, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Slider } from '@/components/ui/slider'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import OutlineTree from './OutlineTree.vue'
import { firstLeaf, type OutlineResponse, type TextUnit } from './types'
import {
  ensureZenFont,
  loadZenPrefs,
  readSessionUserId,
  saveZenPrefs,
  ZEN_FONT_OPTIONS,
  ZEN_SIZE_MAX,
  ZEN_SIZE_MIN,
  ZEN_SIZE_PRESETS,
  ZEN_THEME_OPTIONS,
  type ZenPrefs,
} from './zen'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => String(route.params.documentId))
const versionId = computed(() => String(route.params.versionId))
const selectedPath = computed(() => (typeof route.query.path === 'string' ? route.query.path : ''))
const userId = readSessionUserId()
const prefs = reactive<ZenPrefs>(loadZenPrefs(userId))
const outlineOpen = ref(false)
const theme = computed(
  () => ZEN_THEME_OPTIONS.find((item) => item.id === prefs.theme) ?? ZEN_THEME_OPTIONS[0],
)
const font = computed(
  () => ZEN_FONT_OPTIONS.find((item) => item.id === prefs.font) ?? ZEN_FONT_OPTIONS[0],
)

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

function replaceQuery(path = selectedPath.value) {
  const query: Record<string, string> = {}
  if (path) {
    query.path = path
  }
  const extra = Object.keys(route.query).some((key) => key !== 'path')
  if (route.query.path === query.path && !extra) {
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
        replaceQuery(path)
      }
    }
  },
  { immediate: true },
)

watch(
  () => route.query,
  () => {
    if (Object.keys(route.query).some((key) => key !== 'path')) {
      replaceQuery()
    }
  },
  { immediate: true },
)

watch(
  prefs,
  (value) => {
    saveZenPrefs(userId, value)
    ensureZenFont(value.font)
  },
  { deep: true, immediate: true },
)

function selectPath(path: string) {
  outlineOpen.value = false
  replaceQuery(path)
}

function onSlider(values: number[] | undefined) {
  const next = values?.[0]
  if (next == null) {
    return
  }
  prefs.size = next
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
      :class="prefs.theme === 'night' ? 'dark' : undefined"
      :style="{
        '--zen-size': `${prefs.size}px`,
        '--zen-bg': theme.bg,
        '--zen-fg': theme.fg,
        '--zen-muted': theme.muted,
        '--font-family-classical': font.stack,
      }"
      :data-reader-font="prefs.font"
      :data-zen-theme="prefs.theme"
      :data-zen-wide="prefs.wide ? '1' : '0'"
    >
      <header class="flex shrink-0 items-center gap-2 px-3 py-2">
        <Button size="sm" type="button" variant="ghost" @click="exitZen">退出</Button>
        <Button class="md:hidden" size="sm" type="button" variant="ghost" @click="outlineOpen = !outlineOpen">
          <PanelLeftIcon />
          目录
        </Button>
        <div class="ml-auto">
          <Popover>
            <Tooltip>
              <TooltipTrigger as-child>
                <PopoverTrigger as-child>
                  <Button size="icon-sm" type="button" variant="ghost" aria-label="阅读配置">
                    <SettingsIcon />
                  </Button>
                </PopoverTrigger>
              </TooltipTrigger>
              <TooltipContent>阅读配置</TooltipContent>
            </Tooltip>
            <PopoverContent class="w-[22rem]" align="end">
              <div class="mb-3 flex items-center justify-between gap-2">
                <p class="text-sm font-medium">阅读配置</p>
                <PopoverClose as-child>
                  <Button size="icon-sm" type="button" variant="ghost" aria-label="关闭">
                    <XIcon />
                  </Button>
                </PopoverClose>
              </div>
              <div class="grid gap-4">
                <div class="grid gap-2">
                  <div class="flex items-center justify-between gap-2">
                    <Label>字体大小</Label>
                    <span class="text-muted-foreground text-xs">{{ prefs.size }}px</span>
                  </div>
                  <div class="flex flex-wrap gap-1">
                    <Button
                      size="sm"
                      type="button"
                      :variant="prefs.size === ZEN_SIZE_PRESETS.small ? 'secondary' : 'ghost'"
                      :aria-pressed="prefs.size === ZEN_SIZE_PRESETS.small"
                      @click="prefs.size = ZEN_SIZE_PRESETS.small"
                    >
                      小
                    </Button>
                    <Button
                      size="sm"
                      type="button"
                      :variant="prefs.size === ZEN_SIZE_PRESETS.medium ? 'secondary' : 'ghost'"
                      :aria-pressed="prefs.size === ZEN_SIZE_PRESETS.medium"
                      @click="prefs.size = ZEN_SIZE_PRESETS.medium"
                    >
                      中
                    </Button>
                    <Button
                      size="sm"
                      type="button"
                      :variant="prefs.size === ZEN_SIZE_PRESETS.large ? 'secondary' : 'ghost'"
                      :aria-pressed="prefs.size === ZEN_SIZE_PRESETS.large"
                      @click="prefs.size = ZEN_SIZE_PRESETS.large"
                    >
                      大
                    </Button>
                  </div>
                  <Slider
                    :model-value="[prefs.size]"
                    :min="ZEN_SIZE_MIN"
                    :max="ZEN_SIZE_MAX"
                    :step="1"
                    aria-label="正文字号"
                    @update:model-value="onSlider"
                  />
                </div>
                <div class="grid gap-2">
                  <Label>正文字体</Label>
                  <div class="flex rounded-lg border p-0.5">
                    <Button
                      v-for="item in ZEN_FONT_OPTIONS"
                      :key="item.id"
                      class="flex-1"
                      size="sm"
                      type="button"
                      :variant="prefs.font === item.id ? 'default' : 'ghost'"
                      :aria-pressed="prefs.font === item.id"
                      @click="prefs.font = item.id"
                    >
                      {{ item.label }}
                    </Button>
                  </div>
                </div>
                <div class="grid gap-2">
                  <Label>背景模式</Label>
                  <div class="flex rounded-lg border p-0.5">
                    <Button
                      v-for="item in ZEN_THEME_OPTIONS"
                      :key="item.id"
                      class="flex-1"
                      size="sm"
                      type="button"
                      :variant="prefs.theme === item.id ? 'default' : 'ghost'"
                      :aria-pressed="prefs.theme === item.id"
                      @click="prefs.theme = item.id"
                    >
                      {{ item.label }}
                    </Button>
                  </div>
                </div>
                <div class="grid gap-2">
                  <Label>栏宽</Label>
                  <div class="flex rounded-lg border p-0.5">
                    <Button
                      class="flex-1"
                      size="sm"
                      type="button"
                      :variant="!prefs.wide ? 'default' : 'ghost'"
                      :aria-pressed="!prefs.wide"
                      @click="prefs.wide = false"
                    >
                      窄栏
                    </Button>
                    <Button
                      class="flex-1"
                      size="sm"
                      type="button"
                      :variant="prefs.wide ? 'default' : 'ghost'"
                      :aria-pressed="prefs.wide"
                      @click="prefs.wide = true"
                    >
                      全宽
                    </Button>
                  </div>
                </div>
              </div>
            </PopoverContent>
          </Popover>
        </div>
      </header>
      <div class="flex min-h-0 flex-1">
        <aside
          class="zen-outline h-full min-h-0 w-64 shrink-0 flex-col border-r"
          :class="outlineOpen ? 'flex' : 'hidden md:flex'"
        >
          <ScrollArea class="h-full px-2 py-3">
            <OutlineTree
              v-if="outline.data.value?.nodes.length"
              :nodes="outline.data.value.nodes"
              :selected-path="selectedPath"
              @select="selectPath"
            />
            <p v-else class="text-muted-foreground px-2 text-sm">还没有目录。</p>
          </ScrollArea>
        </aside>
        <ScrollArea class="min-w-0 flex-1">
          <div
            class="zen-body px-6 py-8"
            :class="prefs.wide ? 'w-full' : 'mx-auto max-w-[42em]'"
          >
            <p v-for="unit in units.data.value ?? []" :key="unit.id" class="zen-classical">
              {{ unit.displayText }}
            </p>
            <p
              v-if="selectedPath && !(units.data.value ?? []).length && !units.isPending.value"
              class="text-muted-foreground text-sm"
            >
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
  --background: var(--zen-bg);
  --foreground: var(--zen-fg);
  --muted-foreground: var(--zen-muted);
  --muted: color-mix(in oklab, var(--zen-fg) 8%, transparent);
  --border: color-mix(in oklab, var(--zen-fg) 12%, transparent);
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
