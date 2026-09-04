<script setup lang="ts">
import { ref } from 'vue'
import { EyeIcon, EyeOffIcon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

const model = defineModel<string>({ default: '' })

withDefaults(
  defineProps<{
    id: string
    autocomplete?: string
  }>(),
  { autocomplete: 'new-password' },
)

const visible = ref(false)
</script>

<template>
  <div class="relative">
    <Input
      :id="id"
      v-model="model"
      class="pr-8"
      :type="visible ? 'text' : 'password'"
      :autocomplete="autocomplete"
      required
    />
    <Button
      class="absolute top-1/2 right-0.5 -translate-y-1/2"
      size="icon-xs"
      variant="ghost"
      type="button"
      :aria-label="visible ? '隐藏密码' : '显示密码'"
      @click="visible = !visible"
    >
      <EyeOffIcon v-if="visible" />
      <EyeIcon v-else />
    </Button>
  </div>
</template>
