<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { KeyRoundIcon, PencilIcon } from '@lucide/vue'
import AppLayout from '@/layouts/AppLayout.vue'
import SearchField from '@/components/SearchField.vue'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableEmpty, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { toast } from 'vue-sonner'
import PasswordInput from './PasswordInput.vue'

interface UserItem {
  id: string
  username: string
  displayName: string
  status: string
  platformRole: string
}

const queryClient = useQueryClient()
const qInput = ref('')
const q = ref('')
const creating = ref(false)
const createUsername = ref('')
const createDisplayName = ref('')
const createPassword = ref('')
const createPasswordConfirm = ref('')
const createError = ref('')
const createSaving = ref(false)
const editing = ref<UserItem | null>(null)
const editUsername = ref('')
const editError = ref('')
const editSaving = ref(false)
const resetting = ref<UserItem | null>(null)
const resetPassword = ref('')
const resetPasswordConfirm = ref('')
const resetError = ref('')
const resetSaving = ref(false)

const users = useQuery({
  queryKey: computed(() => ['users', q.value]),
  queryFn: () => {
    const params = q.value ? `?q=${encodeURIComponent(q.value)}` : ''
    return api<UserItem[]>(`/api/users${params}`)
  },
})

const createPasswordsMatch = computed(
  () => Boolean(createPassword.value) && createPassword.value === createPasswordConfirm.value,
)
const resetPasswordsMatch = computed(
  () => Boolean(resetPassword.value) && resetPassword.value === resetPasswordConfirm.value,
)

function runSearch() {
  q.value = qInput.value.trim()
}

function clearSearch() {
  q.value = ''
}

function openCreate() {
  creating.value = true
  createUsername.value = ''
  createDisplayName.value = ''
  createPassword.value = ''
  createPasswordConfirm.value = ''
  createError.value = ''
}

function onCreateOpen(open: boolean) {
  creating.value = open
  if (!open) {
    createError.value = ''
  }
}

async function confirmCreate() {
  if (!createUsername.value.trim() || !createDisplayName.value.trim() || !createPassword.value) {
    createError.value = '请填写用户名、显示名和密码'
    return
  }
  if (!createPasswordsMatch.value) {
    createError.value = '两次输入的密码不一致'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await api('/api/users', {
      method: 'POST',
      body: JSON.stringify({
        username: createUsername.value.trim(),
        displayName: createDisplayName.value.trim(),
        password: createPassword.value,
        platformRole: 'user',
      }),
    })
    toast.success('用户已创建')
    creating.value = false
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  } catch (err) {
    createError.value = err instanceof ApiError ? err.message : '创建失败'
  } finally {
    createSaving.value = false
  }
}

function openEdit(user: UserItem) {
  editing.value = user
  editUsername.value = user.username
  editError.value = ''
}

function onEditOpen(open: boolean) {
  if (!open) {
    editing.value = null
    editError.value = ''
  }
}

async function saveEdit() {
  if (!editing.value || !editUsername.value.trim()) {
    editError.value = '请填写用户名'
    return
  }
  editSaving.value = true
  editError.value = ''
  try {
    await api(`/api/users/${editing.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ username: editUsername.value.trim() }),
    })
    toast.success('已保存用户名')
    editing.value = null
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  } catch (err) {
    editError.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    editSaving.value = false
  }
}

function openReset(user: UserItem) {
  resetting.value = user
  resetPassword.value = ''
  resetPasswordConfirm.value = ''
  resetError.value = ''
}

function onResetOpen(open: boolean) {
  if (!open) {
    resetting.value = null
    resetError.value = ''
  }
}

async function confirmReset() {
  if (!resetting.value || !resetPassword.value) {
    resetError.value = '请输入新密码'
    return
  }
  if (!resetPasswordsMatch.value) {
    resetError.value = '两次输入的密码不一致'
    return
  }
  resetSaving.value = true
  resetError.value = ''
  try {
    await api(`/api/users/${resetting.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ password: resetPassword.value }),
    })
    toast.success('密码已重置')
    resetting.value = null
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  } catch (err) {
    resetError.value = err instanceof ApiError ? err.message : '重置失败'
  } finally {
    resetSaving.value = false
  }
}
</script>

<template>
  <AppLayout>
    <div class="mx-auto max-w-6xl space-y-6">
      <div class="flex flex-wrap items-center gap-2">
        <SearchField
          v-model="qInput"
          placeholder="搜索用户名、显示名"
          @search="runSearch"
          @clear="clearSearch"
        />
        <Button class="ml-auto" type="button" @click="openCreate">新建用户</Button>
      </div>
      <p v-if="users.isError.value" class="text-destructive">无法加载用户</p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>用户名</TableHead>
            <TableHead>显示名</TableHead>
            <TableHead>角色</TableHead>
            <TableHead>状态</TableHead>
            <TableHead class="text-right">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableEmpty v-if="users.data.value?.length === 0" :colspan="5">还没有用户</TableEmpty>
          <TableRow v-for="user in users.data.value ?? []" :key="user.id">
            <TableCell>{{ user.username }}</TableCell>
            <TableCell>{{ user.displayName }}</TableCell>
            <TableCell>{{ user.platformRole }}</TableCell>
            <TableCell>
              <Badge variant="secondary">{{ user.status }}</Badge>
            </TableCell>
            <TableCell>
              <div class="flex items-center justify-end gap-0.5">
                <Tooltip>
                  <TooltipTrigger as-child>
                    <Button size="icon-sm" variant="ghost" type="button" aria-label="编辑" @click="openEdit(user)">
                      <PencilIcon />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>编辑</TooltipContent>
                </Tooltip>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <Button size="icon-sm" variant="ghost" type="button" aria-label="重置密码" @click="openReset(user)">
                      <KeyRoundIcon />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>重置密码</TooltipContent>
                </Tooltip>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
    <Dialog :open="creating" @update:open="onCreateOpen">
      <DialogContent>
        <form class="grid gap-4" @submit.prevent="confirmCreate">
          <DialogHeader>
            <DialogTitle>新建用户</DialogTitle>
            <DialogDescription>填写账号基本信息，确认后创建。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2">
            <Label for="create-username">用户名</Label>
            <Input id="create-username" v-model="createUsername" autocomplete="off" required />
          </div>
          <div class="grid gap-2">
            <Label for="create-display-name">显示名</Label>
            <Input id="create-display-name" v-model="createDisplayName" required />
          </div>
          <div class="grid gap-2">
            <Label for="create-password">密码</Label>
            <PasswordInput id="create-password" v-model="createPassword" />
          </div>
          <div class="grid gap-2">
            <Label for="create-password-confirm">确认密码</Label>
            <PasswordInput id="create-password-confirm" v-model="createPasswordConfirm" />
          </div>
          <p
            v-if="createPasswordConfirm && !createPasswordsMatch"
            class="text-sm text-destructive"
          >
            两次输入的密码不一致
          </p>
          <p v-if="createError" class="text-sm text-destructive">{{ createError }}</p>
          <DialogFooter>
            <Button type="button" variant="outline" @click="onCreateOpen(false)">取消</Button>
            <Button type="submit" :disabled="createSaving || !createPasswordsMatch">确认</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(editing)" @update:open="onEditOpen">
      <DialogContent>
        <form class="grid gap-4" @submit.prevent="saveEdit">
          <DialogHeader>
            <DialogTitle>编辑用户</DialogTitle>
            <DialogDescription>修改用户名。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2">
            <Label for="edit-username">用户名</Label>
            <Input id="edit-username" v-model="editUsername" required />
          </div>
          <p v-if="editError" class="text-sm text-destructive">{{ editError }}</p>
          <DialogFooter>
            <Button type="button" variant="outline" @click="onEditOpen(false)">取消</Button>
            <Button type="submit" :disabled="editSaving">保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
    <Dialog :open="Boolean(resetting)" @update:open="onResetOpen">
      <DialogContent>
        <form class="grid gap-4" @submit.prevent="confirmReset">
          <DialogHeader>
            <DialogTitle>重置密码</DialogTitle>
            <DialogDescription>为 {{ resetting?.username }} 设置新密码。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2">
            <Label for="reset-password">新密码</Label>
            <PasswordInput id="reset-password" v-model="resetPassword" />
          </div>
          <div class="grid gap-2">
            <Label for="reset-password-confirm">确认密码</Label>
            <PasswordInput id="reset-password-confirm" v-model="resetPasswordConfirm" />
          </div>
          <p
            v-if="resetPasswordConfirm && !resetPasswordsMatch"
            class="text-sm text-destructive"
          >
            两次输入的密码不一致
          </p>
          <p v-if="resetError" class="text-sm text-destructive">{{ resetError }}</p>
          <DialogFooter>
            <Button type="button" variant="outline" @click="onResetOpen(false)">取消</Button>
            <Button type="submit" :disabled="resetSaving || !resetPasswordsMatch">确认</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </AppLayout>
</template>
