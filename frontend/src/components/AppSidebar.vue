<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { BookOpenIcon, SearchIcon, UsersIcon } from '@lucide/vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'

const route = useRoute()
const me = computed(() => {
  const raw = sessionStorage.getItem('ontotrace.me')
  return raw ? (JSON.parse(raw) as { username: string; displayName: string; platformRole: string }) : null
})

const items = computed(() => {
  const nav = [
    { title: '文档库', to: '/documents', icon: BookOpenIcon, match: (path: string) => path.startsWith('/documents') },
    { title: '检索', to: '/search', icon: SearchIcon, match: (path: string) => path.startsWith('/search') },
  ]
  if (me.value?.platformRole === 'admin') {
    nav.push({ title: '用户', to: '/users', icon: UsersIcon, match: (path: string) => path.startsWith('/users') })
  }
  return nav
})
</script>

<template>
  <Sidebar collapsible="icon">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size="lg" as-child>
            <RouterLink to="/documents">
              <span class="font-medium">溯知</span>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupLabel>工作台</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in items" :key="item.to">
              <SidebarMenuButton as-child :is-active="item.match(route.path)" :tooltip="item.title">
                <RouterLink :to="item.to">
                  <component :is="item.icon" />
                  <span>{{ item.title }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
    <SidebarFooter>
      <p class="truncate px-2 text-sm text-muted-foreground group-data-[collapsible=icon]:hidden">
        {{ me?.displayName }}
      </p>
    </SidebarFooter>
    <SidebarRail />
  </Sidebar>
</template>
