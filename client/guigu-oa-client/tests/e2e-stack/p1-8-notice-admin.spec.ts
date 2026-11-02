import { test, expect } from './helpers/fixtures'
import { marker } from './helpers/stack'
import { confirmMessageBox, rowAcrossPages, uiLoginExpectWork } from './helpers/ui'
import type { Oracle } from './helpers/oracle'
import type { Page } from '@playwright/test'

/**
 * P1-8（#220）：公告发布管理页的真实栈验收。
 *
 * admin 在 /system/notice 新建草稿（含一张图片上传）→ 发布 → 普通用户铃铛出现并打开
 * 详情看到图片（认证取回为 blob）→ admin 撤回后铃铛与列表消失 → admin 删除。
 *
 * 先把这个用户已有的未读全部读掉，角标从 0 起算；自造公告用完即删。
 */

interface FeedPage {
  records: { id: number }[]
  total: number
  pages: number
}

async function markAllRead(oracle: Oracle, user: string): Promise<void> {
  for (let page = 1; page <= 20; page += 1) {
    const body = await oracle.call<FeedPage>(
      user,
      'get',
      `/admin/notices/feed?page=${page}&limit=100&unreadOnly=true`,
    )
    for (const row of body.records) {
      await oracle.call(user, 'post', `/admin/notices/${row.id}/read`)
    }
    if (body.records.length < 100) return
  }
}

/** 1x1 透明 PNG：服务端按魔数校验，不能用任意字节冒充。 */
const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
)

async function logout(page: Page) {
  await page.locator('.logout-btn').click()
  await expect(page.getByPlaceholder('用户名')).toBeVisible()
}

test('管理页草稿→发布→铃铛→撤回→删除，含图片上传与渲染', async ({ page, oracle }) => {
  const title = marker('公告管理')
  let noticeId: number | undefined

  try {
    await markAllRead(oracle, 'user')

    await uiLoginExpectWork(page, 'admin')
    await page.goto('/system/notice')
    await expect(page.getByRole('heading', { name: '通知公告' })).toBeVisible()

    await page.getByRole('button', { name: '新建', exact: true }).click()
    await expect(page.getByRole('heading', { name: '新建公告' })).toBeVisible()

    await page.getByPlaceholder('公告标题').fill(title)
    await page.locator('.ql-editor').click()
    await page.keyboard.type(`正文 ${title}`)
    const chooser = page.waitForEvent('filechooser')
    await page.locator('.ql-toolbar .ql-image').click()
    const fileChooser = await chooser
    await fileChooser.setFiles({
      name: 'notice.png',
      mimeType: 'image/png',
      buffer: PNG_1X1,
    })
    // 编辑器里的原生 img 带不上 token：水合成 blob 预览，服务端地址留在 data-notice-src。
    const editorImage = page.locator('.ql-editor img')
    await expect(editorImage).toHaveAttribute('data-notice-src', /^\/admin\/notices\/images\/\d+$/)
    await expect(editorImage).toHaveAttribute('src', /^blob:/)
    await expect
      .poll(() => editorImage.evaluate((image) => (image as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(0)

    await page.getByRole('tab', { name: '预览' }).click()
    const previewImage = page.locator('.notice-content img')
    await expect(previewImage).toHaveAttribute('src', /^blob:/)
    await expect
      .poll(() => previewImage.evaluate((image) => (image as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(0)

    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page).toHaveURL(/\/system\/notice\/edit\/\d+$/)
    await expect(page.locator('.notice-meta-row')).toContainText('草稿')
    noticeId = Number(/\/system\/notice\/edit\/(\d+)$/.exec(page.url())?.[1])
    expect(noticeId).toBeGreaterThan(0)
    // 保存载荷只写服务端地址，绝不能把 blob 地址落库。
    const saved = await oracle.call<{ content: string }>(
      'admin',
      'get',
      `/admin/system/notices/${noticeId}`,
    )
    expect(saved.content).toMatch(/\/admin\/notices\/images\/\d+/)
    expect(saved.content).not.toContain('blob:')

    await page.getByRole('button', { name: '发布', exact: true }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.notice-meta-row')).toContainText('已发布')

    await logout(page)
    await uiLoginExpectWork(page, 'user')
    const badge = page.locator('.top-bar .el-badge__content')
    await expect(badge).toHaveText('1')

    await page.getByRole('button', { name: '公告', exact: true }).click()
    const item = page.locator('.notice-item', { hasText: title })
    await expect(item).toBeVisible()
    await item.click()

    await expect(page).toHaveURL(new RegExp(`/notice/${noticeId}$`))
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await expect(page.locator('.notice-content')).toContainText(`正文 ${title}`)
    const detailImage = page.locator('.notice-content img')
    await expect(detailImage).toHaveAttribute('src', /^blob:/)
    await expect
      .poll(() => detailImage.evaluate((image) => (image as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(0)
    await expect(badge).toBeHidden()

    await logout(page)
    await uiLoginExpectWork(page, 'admin')
    await page.goto('/system/notice')
    const publishedRow = await rowAcrossPages(page, title)
    expect(publishedRow).not.toBeNull()
    await publishedRow!.getByRole('button', { name: '撤回', exact: true }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.el-table__row', { hasText: title })).toContainText('已撤回')

    await logout(page)
    await uiLoginExpectWork(page, 'user')
    await expect(page.locator('.top-bar .el-badge__content')).toBeHidden()
    await page.goto('/notice')
    await expect(page.getByRole('heading', { name: '全部公告' })).toBeVisible()
    await expect(page.locator('.notice-table')).not.toContainText(title)

    await logout(page)
    await uiLoginExpectWork(page, 'admin')
    await page.goto('/system/notice')
    const withdrawnRow = await rowAcrossPages(page, title)
    expect(withdrawnRow).not.toBeNull()
    await withdrawnRow!.getByRole('button', { name: '删除', exact: true }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.el-table__row', { hasText: title })).toHaveCount(0)
  } finally {
    if (noticeId) {
      await oracle
        .call('admin', 'delete', `/admin/system/notices/${noticeId}`)
        .catch(() => undefined)
    }
  }
})
