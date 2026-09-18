<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { BookOpenTextIcon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { zenReadingLocation } from './zen'

const props = defineProps<{
  documentId: string
  versionId?: string | null
  path?: string
}>()

const enabled = computed(() => Boolean(props.versionId))
const to = computed(() =>
  props.versionId ? zenReadingLocation(props.documentId, props.versionId, props.path) : '',
)
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <span class="inline-flex">
        <Button
          v-if="enabled"
          size="icon-sm"
          variant="ghost"
          as-child
          aria-label="纯净阅读"
        >
          <RouterLink :to="to">
            <BookOpenTextIcon />
          </RouterLink>
        </Button>
        <Button
          v-else
          size="icon-sm"
          variant="ghost"
          type="button"
          aria-label="纯净阅读"
          disabled
        >
          <BookOpenTextIcon />
        </Button>
      </span>
    </TooltipTrigger>
    <TooltipContent>{{ enabled ? '纯净阅读' : '请先提取内容' }}</TooltipContent>
  </Tooltip>
</template>
