<script setup lang="ts">
import { computed, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { BanIcon, CircleCheckIcon, Loader2Icon, OctagonXIcon } from '@lucide/vue'
import { api } from '@/api/client'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { isJobTerminal, jobStatusLabel, type Job } from './jobs'

const props = defineProps<{
  jobId: string
}>()

const emit = defineEmits<{
  done: []
}>()

const job = useQuery({
  queryKey: computed(() => ['job', props.jobId]),
  queryFn: () => api<Job>(`/api/jobs/${props.jobId}`),
  refetchInterval: (query) => (isJobTerminal(query.state.data?.status) ? false : 2000),
})

const status = computed(() => job.data.value?.status)
const tooltip = computed(() => {
  const current = job.data.value
  if (!current) {
    return '正在查询任务'
  }
  const parts = [jobStatusLabel(current.status)]
  if (current.stage) {
    parts.push(current.stage)
  }
  if (current.errorSummary) {
    parts.push(current.errorSummary)
  }
  return parts.join(' · ')
})

watch(
  () => job.data.value?.status,
  (value) => {
    if (isJobTerminal(value)) {
      emit('done')
    }
  },
)
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <span
        class="inline-flex size-7 items-center justify-center text-muted-foreground"
        :aria-label="tooltip"
      >
        <Loader2Icon
          v-if="!status || status === 'queued' || status === 'running'"
          class="size-4 animate-spin"
        />
        <CircleCheckIcon v-else-if="status === 'succeeded'" class="size-4" />
        <OctagonXIcon v-else-if="status === 'failed'" class="size-4 text-destructive" />
        <BanIcon v-else class="size-4" />
      </span>
    </TooltipTrigger>
    <TooltipContent>{{ tooltip }}</TooltipContent>
  </Tooltip>
</template>
