import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import {
  expectWorkListLoaded,
  nextWorkListPage,
  openWorkList,
  uiLoginExpectWork,
  workListItems,
} from './helpers/ui'

/**
 * P1-4（#121）：真实多页数据翻页（含末页与越界页）。
 * 现场用 API 造 25 条带标记审批；bob 在待处理列表翻页核对。
 * 页数按真实总数动态计算（栈上数据跨轮积累，列表总行数不固定）；
 * 末页不满、下一页禁用、越界页 API 返回空。
 */

test('25 条真实数据翻页：末页不满、下一页禁用、越界为空', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const base = marker('分页')
  const reasons: string[] = []
  for (let i = 1; i <= 25; i++) {
    const reason = `${base}-${String(i).padStart(2, '0')}`
    await oracle.call('alice', 'post', '/admin/process/startUp', {
      processTemplateId: template.id,
      processTypeId: template.processTypeId,
      formValues: JSON.stringify({
        formData: { reason, days: 1 },
        formShowData: { 请假原因: reason, 请假天数: 1 },
      }),
      attachmentIds: [],
    })
    reasons.push(reason)
  }

  // oracle：以真实总数算页数，越界页为空。
  const first = await oracle.workList('bob', 'pending', 1, 10)
  const pages = Math.ceil(first.total / 10)
  expect(pages, '造数后待处理应多于两页').toBeGreaterThan(2)
  const beyond = await oracle.workList('bob', 'pending', pages + 1, 10)
  expect(beyond.records).toHaveLength(0)

  await uiLoginExpectWork(page, 'bob')
  await openWorkList(page, '待处理')
  // 首轮扫描前等初始加载完成，翻页换等 find 响应（#169）：固定 sleep 在慢 runner
  // 上会扫到空页/提前翻页，漏掉第 1 页或跳过目标行。
  await expectWorkListLoaded(page)

  const seen = new Set<string>()
  for (let pageNo = 1; pageNo <= pages; pageNo++) {
    const rows = workListItems(page)
    const count = await rows.count()
    expect(count, `第 ${pageNo} 页行数不超过页大小`).toBeLessThanOrEqual(10)
    const texts = await rows.allInnerTexts()
    texts.forEach((text) => {
      reasons.forEach((reason) => {
        if (text.includes(reason)) seen.add(reason)
      })
    })
    if (pageNo < pages) {
      await nextWorkListPage(page)
    }
  }
  expect(seen.size, '翻完全部页应看到全部 25 条标记审批').toBe(25)

  // 末页：下一页按钮禁用（UI 到不了越界页）。
  await expect(page.locator('.el-pagination .btn-next')).toBeDisabled()
})
