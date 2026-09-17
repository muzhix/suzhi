import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '@/features/auth/LoginPage.vue'
import DocumentListPage from '@/features/documents/DocumentListPage.vue'
import DocumentDetailPage from '@/features/documents/DocumentDetailPage.vue'
import ReaderPage from '@/features/reader/ReaderPage.vue'
import SearchPage from '@/features/search/SearchPage.vue'
import UserListPage from '@/features/users/UserListPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage },
    { path: '/', redirect: '/documents' },
    { path: '/documents', component: DocumentListPage },
    { path: '/documents/:documentId', component: DocumentDetailPage },
    {
      path: '/documents/:documentId/versions/:versionId/read',
      component: ReaderPage,
    },
    { path: '/search', component: SearchPage },
    { path: '/users', component: UserListPage },
  ],
})

router.beforeEach((to) => {
  const authed = sessionStorage.getItem('ontotrace.me')
  if (to.path !== '/login' && !authed) {
    return '/login'
  }
  if (to.path === '/login' && authed) {
    return '/documents'
  }
  return true
})
