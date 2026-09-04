<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <span
    role="link"
    aria-disabled="true"
    aria-current="page"
    data-slot="breadcrumb-page"
    :class="cn('text-foreground font-normal', props.class)"
  >
    <slot />
  </span>
</template>
