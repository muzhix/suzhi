<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { PanelLeftIcon, SettingsIcon, XIcon } from '@lucide/vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Popover, PopoverClose, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Slider } from '@/components/ui/slider'
import { TooltipProvider } from '@/components/ui/tooltip'
import OutlineTree from './OutlineTree.vue'
import { firstLeaf, type OutlineResponse, type TextUnit } from './types'
import {
  ensureZenFont,
  loadZenPrefs,
  readSessionUserId,
  saveZenPrefs,
  zenChromeStayOpen,
  zenParagraphLabel,
  zenThemeVars,
  ZEN_CHROME_HIDE_MS,
  ZEN_CHROME_HOTZONE_PX,
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
const chromeOpen = ref(false)
const overChrome = ref(false)
const prefsOpen = ref(false)
const pointerY = ref<number | null>(null)
let chromeHideTimer: ReturnType<typeof setTimeout> | undefined
const theme = computed(
  () => ZEN_THEME_OPTIONS.find((item) => item.id === prefs.theme) ?? ZEN_THEME_OPTIONS[0],
)
const font = computed(
  () => ZEN_FONT_OPTIONS.find((item) => item.id === prefs.font) ?? ZEN_FONT_OPTIONS[0],
)
const shellStyle = computed<Record<string, string>>(() => ({
  '--zen-size': `${prefs.size}px`,
  '--font-family-classical': font.value.stack,
  ...zenThemeVars(theme.value),
}))

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

const textUnits = computed(() => units.data.value ?? [])

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

function revealChrome() {
  if (chromeHideTimer !== undefined) {
    clearTimeout(chromeHideTimer)
    chromeHideTimer = undefined
  }
  chromeOpen.value = true
}

function hideChromeSoon() {
  if (!chromeOpen.value || zenChromeStayOpen(overChrome.value, prefsOpen.value, pointerY.value)) {
    return
  }
  if (chromeHideTimer !== undefined) {
    return
  }
  chromeHideTimer = setTimeout(() => {
    chromeHideTimer = undefined
    if (!zenChromeStayOpen(overChrome.value, prefsOpen.value, pointerY.value)) {
      chromeOpen.value = false
    }
  }, ZEN_CHROME_HIDE_MS)
}

function onPagePointerMove(event: PointerEvent) {
  pointerY.value = event.clientY
  if (event.clientY <= ZEN_CHROME_HOTZONE_PX) {
    revealChrome()
    return
  }
  hideChromeSoon()
}

function onPagePointerLeave() {
  pointerY.value = null
  hideChromeSoon()
}

function onPrefsOpen(open: boolean) {
  prefsOpen.value = open
  if (open) {
    revealChrome()
    return
  }
  hideChromeSoon()
}

function onEsc(event: KeyboardEvent) {
  if (event.key !== 'Escape') {
    return
  }
  if (prefsOpen.value) {
    return
  }
  event.stopPropagation()
  revealChrome()
}

onMounted(() => {
  window.addEventListener('keydown', onEsc)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onEsc)
  if (chromeHideTimer !== undefined) {
    clearTimeout(chromeHideTimer)
  }
})

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
      class="zen-page relative flex h-dvh min-h-0 flex-col overflow-hidden"
      :class="prefs.theme === 'night' ? 'dark' : undefined"
      :style="shellStyle"
      :data-reader-font="prefs.font"
      :data-zen-theme="prefs.theme"
      :data-zen-wide="prefs.wide ? '1' : '0'"
      :data-zen-chrome="chromeOpen ? '1' : '0'"
      @pointermove="onPagePointerMove"
      @pointerleave="onPagePointerLeave"
    >
      <header
        class="zen-chrome absolute inset-x-0 top-0 z-20 flex items-center gap-2 px-3 py-2"
        :data-open="chromeOpen ? '1' : '0'"
        :inert="!chromeOpen"
        @pointerenter="overChrome = true; revealChrome()"
        @pointerleave="overChrome = false; hideChromeSoon()"
      >
        <Button size="sm" type="button" variant="ghost" @click="exitZen">退出</Button>
        <Button class="md:hidden" size="sm" type="button" variant="ghost" @click="outlineOpen = !outlineOpen">
          <PanelLeftIcon />
          目录
        </Button>
        <div class="ml-auto">
          <Popover @update:open="onPrefsOpen">
            <PopoverTrigger as-child>
              <Button size="icon-sm" type="button" variant="ghost" aria-label="阅读配置">
                <SettingsIcon />
              </Button>
            </PopoverTrigger>
            <PopoverContent
              class="max-h-[min(36rem,calc(100dvh-3rem))] w-[22rem] overflow-y-auto"
              :class="prefs.theme === 'night' ? 'dark' : undefined"
              :style="zenThemeVars(theme)"
              align="end"
            >
              <div class="mb-2 flex items-center justify-between gap-2">
                <p class="text-sm font-medium">阅读配置</p>
                <PopoverClose as-child>
                  <Button size="icon-sm" type="button" variant="ghost" aria-label="关闭">
                    <XIcon />
                  </Button>
                </PopoverClose>
              </div>
              <div class="grid gap-3">
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
                  <div class="flex flex-wrap gap-0.5 rounded-lg border p-0.5">
                    <Button
                      v-for="item in ZEN_FONT_OPTIONS"
                      :key="item.id"
                      class="min-w-[30%] flex-1"
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
      <div class="flex min-h-0 flex-1" @pointerdown="hideChromeSoon">
        <aside
          class="zen-outline h-full min-h-0 w-64 shrink-0 flex-col border-r transition-[padding-top] duration-150"
          :class="outlineOpen ? 'flex' : 'hidden md:flex'"
          :style="{ paddingTop: chromeOpen ? `${ZEN_CHROME_HOTZONE_PX}px` : '0px' }"
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
            class="zen-body py-8"
            :class="prefs.wide ? 'w-full px-10 md:px-16' : 'mx-auto max-w-[42em] px-6'"
          >
            <template v-for="(unit, index) in textUnits" :key="unit.id">
              <div class="zen-para-rule" aria-hidden="true">
                <span>{{ zenParagraphLabel(index, textUnits.length) }}</span>
              </div>
              <p class="zen-classical">{{ unit.displayText }}</p>
            </template>
            <p
              v-if="selectedPath && !textUnits.length && !units.isPending.value"
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

.zen-chrome {
  background: color-mix(in oklab, var(--zen-bg) 92%, transparent);
  pointer-events: none;
  opacity: 0;
  transform: translateY(-100%);
  transition:
    opacity 150ms ease,
    transform 150ms ease;
}

.zen-chrome[data-open='1'] {
  pointer-events: none;
  opacity: 1;
  transform: none;
}

.zen-chrome[data-open='1'] :deep(button) {
  pointer-events: auto;
}

.zen-para-rule {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 1.25em 0 0.5em;
  color: var(--zen-muted);
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  line-height: 1;
  user-select: none;
}

.zen-para-rule:first-child {
  margin-top: 0;
}

.zen-para-rule::before,
.zen-para-rule::after {
  content: "";
  flex: 1 1 0;
  border-top: 1px solid color-mix(in oklab, var(--zen-fg) 10%, transparent);
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
