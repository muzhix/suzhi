<script setup lang="ts">
import { SearchIcon, XIcon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

const model = defineModel<string>({ default: '' })

defineProps<{
  placeholder?: string
}>()

const emit = defineEmits<{
  search: []
  clear: []
}>()

function clear() {
  model.value = ''
  emit('clear')
}
</script>

<template>
  <form class="relative max-w-sm min-w-48 flex-1" @submit.prevent="emit('search')">
    <Input
      v-model="model"
      class="pr-16"
      :placeholder="placeholder"
      :aria-label="placeholder"
    />
    <div class="absolute inset-y-0 right-0.5 flex items-center">
      <Button
        v-if="model"
        size="icon-xs"
        variant="ghost"
        type="button"
        aria-label="清空"
        @click="clear"
      >
        <XIcon />
      </Button>
      <Button size="icon-xs" variant="ghost" type="submit" aria-label="搜索">
        <SearchIcon />
      </Button>
    </div>
  </form>
</template>
