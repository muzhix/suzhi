import { test, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

function findZhengshi(): { file: string; scheme: string } | null {
  const dir = path.resolve(__dirname, '../../doc/正史')
  if (!fs.existsSync(dir)) {
    console.warn('skip full-book e2e: doc/正史/ 不存在')
    return null
  }
  const files = fs.readdirSync(dir)
  const match = (needles: string[], scheme: string) => {
    const name = files.find((file) => needles.some((needle) => file.includes(needle)))
    return name ? { file: path.join(dir, name), scheme } : null
  }
  return (
    match(['通鉴', '通鑑', '资治通鉴', '資治通鑑'], 'biannian-juan-ji-nian') ||
    match(['旧唐书', '舊唐書'], 'jizhuan-toc-divergent') ||
    match(['史记', '史記'], 'jizhuan-toc-same')
  )
}

test('upload full book, preview scheme, and read a node', async ({ page, request }) => {
  const book = findZhengshi()
  test.skip(!book, 'doc/正史 missing')
  const health = await request.get('http://localhost:8080/api/health')
  test.skip(!health.ok(), 'backend not running')

  await page.goto('/login')
  await page.getByLabel('用户名').fill('hanbd')
  await page.getByLabel('密码').fill('change-me')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/documents/)

  await page.getByRole('button', { name: '新建文档' }).click()
  const title = `全书结构 ${Date.now()}`
  await page.getByLabel('文档名称').fill(title)
  const fileInput = page.locator('#create-file')
  await fileInput.setInputFiles(book!.file)
  await page.getByRole('button', { name: '确认' }).click()
  await expect(page.getByText('文档已创建')).toBeVisible({ timeout: 120_000 })

  await expect(page.getByRole('link', { name: title })).toBeVisible()
  await page.getByRole('button', { name: '提取内容' }).first().click()
  await page.locator('#scheme').selectOption(book!.scheme)
  await page.getByRole('button', { name: '预览目录' }).click()
  await expect(page.getByText('可以确认')).toBeVisible({ timeout: 180_000 })
  await page.getByRole('button', { name: '确认提取' }).click()
  await expect(page.getByLabel(/已完成/)).toBeVisible({ timeout: 180_000 })

  await page.getByRole('link', { name: title }).click()
  await expect(page).toHaveURL(/\/read/)
  await expect(page.getByText('文本单元')).toHaveCount(0)
  await page.locator('[role="treeitem"] button').nth(1).click()
  await expect(page.locator('.reader-text').first()).toBeVisible()
})
