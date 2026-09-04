<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { api } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

interface SearchResponse {
  textHits: { textUnitId: string; documentId: string; documentVersionId: string; snippet: string }[]
  eduHits: { eduId: string; documentVersionId: string; text: string; status: string }[]
}

const query = ref('')
const result = ref<SearchResponse | null>(null)

async function search() {
  result.value = await api<SearchResponse>('/api/search', {
    method: 'POST',
    body: JSON.stringify({ query: query.value }),
  })
}
</script>

<template>
  <AppLayout>
    <div class="mx-auto max-w-3xl space-y-6">
      <form class="flex gap-2" @submit.prevent="search">
        <Input v-model="query" class="flex-1" placeholder="关键词" />
        <Button type="submit">搜索</Button>
      </form>
      <section v-if="result" class="space-y-6">
        <div>
          <h2 class="mb-2 font-medium">原文</h2>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>摘录</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="hit in result.textHits" :key="hit.textUnitId">
                <TableCell>
                  <RouterLink
                    :to="`/documents/${hit.documentId}`"
                    class="hover:underline"
                  >
                    {{ hit.snippet }}
                  </RouterLink>
                </TableCell>
              </TableRow>
              <TableRow v-if="result.textHits.length === 0">
                <TableCell class="text-muted-foreground">没有原文命中</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
        <div>
          <h2 class="mb-2 font-medium">EDU</h2>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>表述</TableHead>
                <TableHead class="w-28">状态</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="hit in result.eduHits" :key="hit.eduId">
                <TableCell>{{ hit.text }}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{{ hit.status }}</Badge>
                </TableCell>
              </TableRow>
              <TableRow v-if="result.eduHits.length === 0">
                <TableCell class="text-muted-foreground" colspan="2">没有 EDU 命中</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
