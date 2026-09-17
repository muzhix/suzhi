<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import { CircleAlertIcon, FileSearchIcon, PencilIcon, SparklesIcon, UploadIcon } from '@lucide/vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import SearchField from '@/components/SearchField.vue'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import PaginationBar from '@/components/ui/pagination/PaginationBar.vue'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import DocumentJobBadge from './DocumentJobBadge.vue'
import ExtractContentDialog from './ExtractContentDialog.vue'
import ExtractEduDialog from './ExtractEduDialog.vue'
import { toast } from 'vue-sonner'
import { uploadDocumentFile } from './uploadFile'

interface DocumentItem {
  id: string
  title: string
  authors?: string
  latestVersionId?: string | null
  updatedAt?: string
}

interface DocumentPage {
  items: DocumentItem[]
  total: number
  page: number
  size: number
}

const ALLOWED_FILE = /\.(txt|md|markdown)$/i

const page = ref(0)
const size = 20
const qInput = ref('')
const q = ref('')
const queryClient = useQueryClient()
const rowJobs = reactive<Record<string, string>>({})
const rowBusy = reactive<Record<string, boolean>>({})
const rowError = reactive<Record<string, string>>({})
const editing = ref<DocumentItem | null>(null)
const editTitle = ref('')
const editAuthors = ref('')
const editError = ref('')
const editSaving = ref(false)
const creating = ref(false)
const createName = ref('')
const createAuthors = ref('')
const createFile = ref<File | null>(null)
const createError = ref('')
const createSaving = ref(false)
const createFileInput = ref<HTMLInputElement | null>(null)
const createDragging = ref(false)
const extractDoc = ref<DocumentItem | null>(null)
const eduDoc = ref<DocumentItem | null>(null)

const list = useQuery({
  queryKey: computed(() => ['documents', page.value, size, q.value]),
  queryFn: () => {
    const params = new URLSearchParams({ page: String(page.value), size: String(size) })
    if (q.value.trim()) {
      params.set('q', q.value.trim())
    }
    return api<DocumentPage>(`/api/documents?${params}`)
  },
})

function runSearch() {
  page.value = 0
  q.value = qInput.value.trim()
}

function clearSearch() {
  if (q.value) {
    page.value = 0
    q.value = ''
  }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString() : '—'
}

function openCreate() {
  creating.value = true
  createName.value = ''
  createAuthors.value = ''
  createFile.value = null
  createError.value = ''
  createDragging.value = false
}

function onCreateOpen(open: boolean) {
  creating.value = open
  if (!open) {
    createError.value = ''
    createFile.value = null
    createDragging.value = false
  }
}

function fileStem(filename: string) {
  return filename.replace(ALLOWED_FILE, '')
}

function acceptCreateFile(file: File) {
  if (!ALLOWED_FILE.test(file.name)) {
    createError.value = '只接受 TXT 或 Markdown'
    return
  }
  createError.value = ''
  createFile.value = file
  if (!createName.value.trim()) {
    createName.value = fileStem(file.name)
  }
}

function onCreateFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) {
    acceptCreateFile(file)
  }
  ;(event.target as HTMLInputElement).value = ''
}

function onCreateDrop(event: DragEvent) {
  createDragging.value = false
  const file = event.dataTransfer?.files[0]
  if (file) {
    acceptCreateFile(file)
  }
}

async function confirmCreate() {
  if (!createName.value.trim() || !createFile.value) {
    createError.value = '请填写文档名称并选择文件'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const doc = await api<DocumentItem>('/api/documents', {
      method: 'POST',
      body: JSON.stringify({ title: createName.value.trim(), authors: createAuthors.value }),
    })
    await uploadDocumentFile(doc.id, createFile.value)
    toast.success('文档已创建')
    creating.value = false
    page.value = 0
    await queryClient.invalidateQueries({ queryKey: ['documents'] })
  } catch (err) {
    createError.value = err instanceof Error ? err.message : '创建失败'
  } finally {
    createSaving.value = false
  }
}

function openEdit(doc: DocumentItem) {
  editing.value = doc
  editTitle.value = doc.title
  editAuthors.value = doc.authors ?? ''
  editError.value = ''
}

function onEditOpen(open: boolean) {
  if (!open) {
    editing.value = null
    editError.value = ''
  }
}

async function saveEdit() {
  if (!editing.value) {
    return
  }
  editSaving.value = true
  editError.value = ''
  try {
    await api(`/api/documents/${editing.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({ title: editTitle.value, authors: editAuthors.value }),
    })
    toast.success('已保存文档信息')
    editing.value = null
    await queryClient.invalidateQueries({ queryKey: ['documents'] })
  } catch (err) {
    editError.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    editSaving.value = false
  }
}

async function upload(doc: DocumentItem, file: File) {
  rowError[doc.id] = ''
  rowBusy[doc.id] = true
  try {
    await uploadDocumentFile(doc.id, file)
    rowError[doc.id] = ''
    toast.success('上传完成，可以提取内容')
  } catch (err) {
    const message = err instanceof Error ? err.message : '上传失败'
    rowError[doc.id] = message
    toast.error(message)
  } finally {
    rowBusy[doc.id] = false
  }
}

function openFilePicker(documentId: string) {
  document.getElementById(`upload-${documentId}`)?.click()
}

async function extractContent(doc: DocumentItem) {
  extractDoc.value = doc
}

async function extractEdu(doc: DocumentItem) {
  if (!doc.latestVersionId) {
    return
  }
  eduDoc.value = doc
}

async function onJobDone(documentId: string) {
  rowBusy[documentId] = false
  await queryClient.invalidateQueries({ queryKey: ['documents'] })
}
</script>

<template>
  <AppLayout>
    <div class="mx-auto max-w-6xl space-y-6">
      <div class="flex flex-wrap items-center gap-2">
        <SearchField
          v-model="qInput"
          placeholder="搜索文档名称、作者"
          @search="runSearch"
          @clear="clearSearch"
        />
        <Button class="ml-auto" type="button" @click="openCreate">新建文档</Button>
      </div>
      <p v-if="list.isError.value" class="text-destructive">无法加载文档</p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>文档名称</TableHead>
            <TableHead>作者</TableHead>
            <TableHead>更新时间</TableHead>
            <TableHead>版本</TableHead>
            <TableHead>任务</TableHead>
            <TableHead class="text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableEmpty v-if="list.data.value?.items.length === 0" :colspan="6">还没有文档</TableEmpty>
          <TableRow v-for="doc in list.data.value?.items ?? []" :key="doc.id">
            <TableCell>
              <RouterLink
                :to="
                  doc.latestVersionId
                    ? `/documents/${doc.id}/versions/${doc.latestVersionId}/read`
                    : `/documents/${doc.id}`
                "
                class="hover:underline"
              >
                {{ doc.title }}
              </RouterLink>
            </TableCell>
            <TableCell>{{ doc.authors || '—' }}</TableCell>
            <TableCell class="whitespace-nowrap">{{ formatTime(doc.updatedAt) }}</TableCell>
            <TableCell>
              <Badge variant="secondary">{{ doc.latestVersionId ? '已提取' : '未提取' }}</Badge>
            </TableCell>
            <TableCell>
              <div class="flex items-center gap-1">
                <DocumentJobBadge v-if="rowJobs[doc.id]" :job-id="rowJobs[doc.id]" @done="onJobDone(doc.id)" />
                <Tooltip v-if="rowError[doc.id]">
                  <TooltipTrigger as-child>
                    <span class="inline-flex size-7 items-center justify-center" :aria-label="rowError[doc.id]">
                      <CircleAlertIcon class="size-4 text-destructive" />
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{{ rowError[doc.id] }}</TooltipContent>
                </Tooltip>
              </div>
            </TableCell>
            <TableCell>
              <div class="flex items-center justify-end gap-0.5">
                <input
                  :id="`upload-${doc.id}`"
                  accept=".txt,.md,.markdown,text/plain,text/markdown"
                  class="sr-only"
                  type="file"
                  @change="
                    (event) => {
                      const file = (event.target as HTMLInputElement).files?.[0]
                      if (file) upload(doc, file)
                      ;(event.target as HTMLInputElement).value = ''
                    }
                  "
                />
                <Tooltip>
                  <TooltipTrigger as-child>
                    <span class="inline-flex">
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        type="button"
                        aria-label="编辑"
                        :disabled="rowBusy[doc.id]"
                        @click="openEdit(doc)"
                      >
                        <PencilIcon />
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>编辑</TooltipContent>
                </Tooltip>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <span class="inline-flex">
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        type="button"
                        aria-label="上传"
                        :disabled="rowBusy[doc.id]"
                        @click="openFilePicker(doc.id)"
                      >
                        <UploadIcon />
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>上传</TooltipContent>
                </Tooltip>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <span class="inline-flex">
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        type="button"
                        aria-label="提取内容"
                        :disabled="rowBusy[doc.id]"
                        @click="extractContent(doc)"
                      >
                        <FileSearchIcon />
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>提取内容</TooltipContent>
                </Tooltip>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <span class="inline-flex">
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        type="button"
                        aria-label="抽取 EDU"
                        :disabled="rowBusy[doc.id] || !doc.latestVersionId"
                        @click="extractEdu(doc)"
                      >
                        <SparklesIcon />
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{{ doc.latestVersionId ? '抽取 EDU' : '请先提取内容' }}</TooltipContent>
                </Tooltip>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <PaginationBar
        v-if="list.data.value"
        :page="list.data.value.page"
        :size="list.data.value.size"
        :total="list.data.value.total"
        @change="page = $event"
      />
    </div>
    <Dialog :open="creating" @update:open="onCreateOpen">
      <DialogContent class="max-w-lg">
        <form class="grid gap-4" @submit.prevent="confirmCreate">
          <DialogHeader>
            <DialogTitle>新建文档</DialogTitle>
            <DialogDescription>填写基本信息并上传 TXT 或 Markdown 文件。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2">
            <Label for="create-name">文档名称</Label>
            <Input id="create-name" v-model="createName" required />
          </div>
          <div class="grid gap-2">
            <Label for="create-authors">作者</Label>
            <Input id="create-authors" v-model="createAuthors" placeholder="可选" />
          </div>
          <div class="grid gap-2">
            <Label>文件</Label>
            <input
              id="create-file"
              ref="createFileInput"
              accept=".txt,.md,.markdown,text/plain,text/markdown"
              class="sr-only"
              type="file"
              @change="onCreateFileChange"
            />
            <button
              class="border-input hover:bg-muted/40 rounded-lg border border-dashed px-4 py-8 text-center text-sm"
              :class="createDragging ? 'bg-muted/60' : 'bg-background'"
              type="button"
              @click="createFileInput?.click()"
              @dragenter.prevent="createDragging = true"
              @dragover.prevent="createDragging = true"
              @dragleave.prevent="createDragging = false"
              @drop.prevent="onCreateDrop"
            >
              <span v-if="createFile" class="text-foreground">{{ createFile.name }}</span>
              <span v-else class="text-muted-foreground">拖拽文件到此处，或点击选择</span>
            </button>
          </div>
          <p v-if="createError" class="text-sm text-destructive">{{ createError }}</p>
          <DialogFooter>
            <Button type="button" variant="outline" @click="onCreateOpen(false)">取消</Button>
            <Button type="submit" :disabled="createSaving">确认</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(editing)" @update:open="onEditOpen">
      <DialogContent>
        <form class="grid gap-4" @submit.prevent="saveEdit">
          <DialogHeader>
            <DialogTitle>编辑文档</DialogTitle>
            <DialogDescription>修改文档名称和作者，不影响已提取的文本和 EDU。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2">
            <Label for="edit-title">文档名称</Label>
            <Input id="edit-title" v-model="editTitle" required />
          </div>
          <div class="grid gap-2">
            <Label for="edit-authors">作者</Label>
            <Input id="edit-authors" v-model="editAuthors" placeholder="可选" />
          </div>
          <p v-if="editError" class="text-sm text-destructive">{{ editError }}</p>
          <DialogFooter>
            <Button type="button" variant="outline" @click="onEditOpen(false)">取消</Button>
            <Button type="submit" :disabled="editSaving">保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
    <ExtractContentDialog
      :open="Boolean(extractDoc)"
      :document-id="extractDoc?.id ?? ''"
      @update:open="(open) => { if (!open) extractDoc = null }"
      @started="
        (jobId) => {
          if (extractDoc) {
            rowError[extractDoc.id] = ''
            rowJobs[extractDoc.id] = jobId
            rowBusy[extractDoc.id] = true
          }
        }
      "
      @error="
        (message) => {
          if (extractDoc) {
            rowError[extractDoc.id] = message
            rowBusy[extractDoc.id] = false
          }
        }
      "
    />
    <ExtractEduDialog
      :open="Boolean(eduDoc?.latestVersionId)"
      :version-id="eduDoc?.latestVersionId ?? ''"
      @update:open="(open) => { if (!open) eduDoc = null }"
      @started="
        (jobId) => {
          if (eduDoc) {
            rowError[eduDoc.id] = ''
            rowJobs[eduDoc.id] = jobId
            rowBusy[eduDoc.id] = true
          }
        }
      "
      @error="
        (message) => {
          if (eduDoc) {
            rowError[eduDoc.id] = message
            rowBusy[eduDoc.id] = false
          }
        }
      "
    />
  </AppLayout>
</template>
