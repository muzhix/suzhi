<script setup lang="ts">
import { ref } from 'vue'
import { ChevronRightIcon } from '@lucide/vue'
import type { OutlineNode } from './types'
import { firstLeafOf } from './types'

const props = defineProps<{
  nodes: OutlineNode[]
  selectedPath: string
}>()

const emit = defineEmits<{
  select: [path: string]
}>()

/** 用户收起的节点。展开仍跟选中路径走，不另做一套树状态。 */
const collapsed = ref(new Set<string>())

function isActive(path: string) {
  return props.selectedPath === path || props.selectedPath.startsWith(`${path}/`)
}

function isExpanded(path: string) {
  return isActive(path) && !collapsed.value.has(path)
}

function onSelect(node: OutlineNode) {
  if (!node.children?.length) {
    emit('select', node.path)
    return
  }
  if (isExpanded(node.path)) {
    const next = new Set(collapsed.value)
    next.add(node.path)
    collapsed.value = next
    return
  }
  const next = new Set(collapsed.value)
  next.delete(node.path)
  collapsed.value = next
  if (!isActive(node.path)) {
    emit('select', firstLeafOf(node))
  }
}
</script>

<template>
  <ul class="space-y-0.5" role="tree">
    <li v-for="node in nodes" :key="node.path" role="treeitem">
      <button
        class="flex w-full items-start gap-1 rounded px-2 py-1 text-left text-sm hover:bg-muted/60"
        :class="selectedPath === node.path ? 'bg-muted font-medium' : isActive(node.path) ? 'text-foreground' : 'text-muted-foreground'"
        type="button"
        :aria-current="selectedPath === node.path ? 'true' : undefined"
        :aria-expanded="node.children?.length ? isExpanded(node.path) : undefined"
        @click="onSelect(node)"
      >
        <ChevronRightIcon
          v-if="node.children?.length"
          class="mt-0.5 size-3.5 shrink-0"
          :class="isExpanded(node.path) ? 'rotate-90' : ''"
        />
        <span v-else class="mt-0.5 inline-block size-3.5 shrink-0" />
        <span class="min-w-0 flex-1">
          {{ node.label }}
          <span
            v-if="node.ganzhi || node.ceYear != null"
            class="ml-1 text-xs font-normal text-muted-foreground"
          >{{ node.ganzhi ? `${node.ganzhi} ` : '' }}{{ node.ceYear != null ? `公元${node.ceYear}年` : '' }}</span>
          <span class="text-muted-foreground"> · {{ node.unitCount }}</span>
        </span>
      </button>
      <div v-if="node.children?.length && isExpanded(node.path)" class="ml-3 border-l pl-1">
        <OutlineTree :nodes="node.children" :selected-path="selectedPath" @select="emit('select', $event)" />
      </div>
    </li>
  </ul>
</template>
