import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginPage from './LoginPage.vue'

describe('LoginPage', () => {
  it('renders login form', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: LoginPage }, { path: '/login', component: LoginPage }],
    })
    await router.push('/login')
    const wrapper = mount(LoginPage, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('登录溯知')
  })
})
