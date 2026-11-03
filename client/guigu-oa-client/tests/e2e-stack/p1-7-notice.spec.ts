import { test, expect } from './helpers/fixtures'
import { marker } from './helpers/stack'
import { uiLoginExpectWork } from './helpers/ui'
import type { Oracle } from './helpers/oracle'

/**
 * P1-7（#219）：公告展示面的真实栈验收。
 *
 * admin 通过服务端接口发布一条公告 → 普通用户登录后铃铛角标出现 → 打开详情自动写
 * 已读回执、角标归零 → admin 撤回后列表与详情都不再可见。
 *
 * 先把这个用户已有的未读全部读掉，角标从 0 起算，避免上一轮失败留下的公告把 1 变成 N。
 * 自造的公告用完即删；已读回执按 ADR 0055 保留。
 */

interface FeedPage {
  records: { id: number }[]
  total: number
  pages: number
}

interface NoticeCreated {
  id: number
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

test('公告发布后铃铛出现，打开详情已读，撤回后消失', async ({ page, oracle }) => {
  const title = marker('公告')
  const user = 'user'
  let noticeId: number | undefined

  try {
    await markAllRead(oracle, user)

    const created = await oracle.call<NoticeCreated>('admin', 'post', '/admin/system/notices', {
      title,
      type: 2,
      content: `<p>正文 <strong>${title}</strong></p>`,
    })
    noticeId = created.id
    await oracle.call('admin', 'post', `/admin/system/notices/${noticeId}/publish`)

    await uiLoginExpectWork(page, user)
    const badge = page.locator('.top-bar .el-badge__content')
    await expect(badge).toHaveText('1')

    await page.getByRole('button', { name: '公告', exact: true }).click()
    const item = page.locator('.notice-item', { hasText: title })
    await expect(item).toBeVisible()
    await item.click()

    await expect(page).toHaveURL(new RegExp(`/notice/${noticeId}$`))
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await expect(page.locator('.notice-content')).toContainText(`正文 ${title}`)
    await expect(badge).toBeHidden()

    await oracle.call('admin', 'post', `/admin/system/notices/${noticeId}/withdraw`)

    await page.goto('/notice')
    await expect(page.getByRole('heading', { name: '全部公告' })).toBeVisible()
    await expect(page.locator('.notice-table')).not.toContainText(title)

    await page.goto(`/notice/${noticeId}`)
    await expect(page.getByText('公告不存在或已下线')).toBeVisible()
  } finally {
    if (noticeId) {
      await oracle
        .call('admin', 'delete', `/admin/system/notices/${noticeId}`)
        .catch(() => undefined)
    }
  }
})
