<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  page: number
  size: number
  total: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <nav class="flex items-center justify-between gap-3 text-sm" aria-label="分页">
    <p class="text-muted-foreground">共 {{ total }} 条</p>
    <div class="flex items-center gap-2">
      <Button size="sm" variant="outline" type="button" :disabled="page <= 0" @click="emit('change', page - 1)">
        上一页
      </Button>
      <span>{{ page + 1 }} / {{ pageCount }}</span>
      <Button
        size="sm"
        variant="outline"
        type="button"
        :disabled="page + 1 >= pageCount"
        @click="emit('change', page + 1)"
      >
        下一页
      </Button>
    </div>
  </nav>
</template>
