<script setup lang="ts">
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

function isActive(path: string) {
  return props.selectedPath === path || props.selectedPath.startsWith(`${path}/`)
}

function onSelect(node: OutlineNode) {
  emit('select', node.children?.length ? firstLeafOf(node) : node.path)
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
        @click="onSelect(node)"
      >
        <ChevronRightIcon
          v-if="node.children?.length"
          class="mt-0.5 size-3.5 shrink-0"
          :class="isActive(node.path) ? 'rotate-90' : ''"
        />
        <span v-else class="mt-0.5 inline-block size-3.5 shrink-0" />
        <span class="min-w-0 flex-1">
          {{ node.label }}
          <span class="text-muted-foreground"> · {{ node.unitCount }}</span>
        </span>
      </button>
      <div v-if="node.children?.length && isActive(node.path)" class="ml-3 border-l pl-1">
        <OutlineTree :nodes="node.children" :selected-path="selectedPath" @select="emit('select', $event)" />
      </div>
    </li>
  </ul>
</template>
