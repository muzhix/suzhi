<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const router = useRouter()
const username = ref('hanbd')
const password = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    const me = await api<{ username: string; displayName: string; platformRole: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    sessionStorage.setItem('ontotrace.me', JSON.stringify(me))
    await router.push('/documents')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '登录失败'
  }
}
</script>

<template>
  <div class="flex min-h-svh items-center justify-center p-6">
    <Card class="w-full max-w-sm">
      <CardHeader>
        <CardTitle>登录溯知</CardTitle>
        <CardDescription>使用引导管理员账号进入文档库</CardDescription>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="submit">
          <div class="space-y-2">
            <Label for="username">用户名</Label>
            <Input id="username" v-model="username" name="username" autocomplete="username" />
          </div>
          <div class="space-y-2">
            <Label for="password">密码</Label>
            <Input id="password" v-model="password" name="password" type="password" autocomplete="current-password" />
          </div>
          <p v-if="error" class="text-sm text-destructive">{{ error }}</p>
          <Button class="w-full" type="submit">登录</Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
