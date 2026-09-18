<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import AppLayout from '@/layouts/AppLayout.vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import DocumentJobBadge from './DocumentJobBadge.vue'
import ZenReadingButton from '@/features/reader/ZenReadingButton.vue'
import ExtractContentDialog from './ExtractContentDialog.vue'

interface DocumentDetail {
  id: string
  title: string
  latestVersionId?: string | null
}

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const documentId = computed(() => String(route.params.documentId))
const extractOpen = ref(false)
const jobId = ref<string | null>(null)
const extracting = ref(false)
const actionError = ref('')

const documentQuery = useQuery({
  queryKey: computed(() => ['document', documentId.value]),
  queryFn: () => api<DocumentDetail>(`/api/documents/${documentId.value}`),
})

const versionId = computed(() => documentQuery.data.value?.latestVersionId || '')

watch(
  () => versionId.value,
  (id) => {
    if (id) {
      void router.replace(`/documents/${documentId.value}/versions/${id}/read`)
    }
  },
  { immediate: true },
)

async function onJobDone() {
  extracting.value = false
  await queryClient.invalidateQueries({ queryKey: ['document', documentId.value] })
}
</script>

<template>
  <AppLayout>
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-end gap-3">
        <ZenReadingButton :document-id="documentId" :version-id="versionId" />
        <Button size="sm" type="button" :disabled="extracting" @click="extractOpen = true">提取内容</Button>
        <DocumentJobBadge v-if="jobId" :job-id="jobId" @done="onJobDone" />
      </div>
      <p v-if="actionError" class="text-sm text-destructive">{{ actionError }}</p>
      <p v-if="!versionId" class="text-sm text-muted-foreground">还没有提取文本。上传文件后选择结构方案并提取。</p>
    </div>
    <ExtractContentDialog
      :open="extractOpen"
      :document-id="documentId"
      @update:open="extractOpen = $event"
      @started="
        (id) => {
          jobId = id
          extracting = true
          actionError = ''
        }
      "
      @error="
        (message) => {
          actionError = message
          extracting = false
        }
      "
    />
  </AppLayout>
</template>
