<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import AppSidebar from '@/components/AppSidebar.vue'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { Separator } from '@/components/ui/separator'
import { Button } from '@/components/ui/button'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { api } from '@/api/client'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => (route.params.documentId ? String(route.params.documentId) : ''))

const documentQuery = useQuery({
  queryKey: computed(() => ['document', documentId.value]),
  queryFn: () => api<{ title: string }>(`/api/documents/${documentId.value}`),
  enabled: computed(() => Boolean(documentId.value)),
})

const me = computed(() => {
  const raw = sessionStorage.getItem('ontotrace.me')
  return raw ? (JSON.parse(raw) as { displayName: string }) : null
})

interface Crumb {
  label: string
  to?: string
}

const crumbs = computed((): Crumb[] => {
  if (route.path.startsWith('/search')) {
    return [{ label: '检索' }]
  }
  if (route.path.startsWith('/users')) {
    return [{ label: '用户' }]
  }
  if (route.path.startsWith('/documents')) {
    const items: Crumb[] = [{ label: '文档库', to: documentId.value ? '/documents' : undefined }]
    if (documentId.value) {
      items.push({
        label: documentQuery.data.value?.title ?? '文档',
      })
    }
    return items
  }
  return [{ label: '溯知' }]
})

async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' })
  } finally {
    sessionStorage.removeItem('ontotrace.me')
    await router.push('/login')
  }
}
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <header class="flex h-12 shrink-0 items-center gap-2 border-b px-4">
        <SidebarTrigger />
        <Separator orientation="vertical" class="h-4" />
        <Breadcrumb>
          <BreadcrumbList>
            <template v-for="(crumb, index) in crumbs" :key="`${crumb.label}-${index}`">
              <BreadcrumbSeparator v-if="index > 0" />
              <BreadcrumbItem>
                <BreadcrumbLink v-if="crumb.to" as-child>
                  <RouterLink :to="crumb.to">{{ crumb.label }}</RouterLink>
                </BreadcrumbLink>
                <BreadcrumbPage v-else>{{ crumb.label }}</BreadcrumbPage>
              </BreadcrumbItem>
            </template>
          </BreadcrumbList>
        </Breadcrumb>
        <div class="ml-auto flex items-center gap-2">
          <span class="text-sm text-muted-foreground">{{ me?.displayName }}</span>
          <Button size="sm" variant="ghost" type="button" @click="logout">退出</Button>
        </div>
      </header>
      <div class="flex-1 p-6">
        <slot />
      </div>
    </SidebarInset>
  </SidebarProvider>
</template>
