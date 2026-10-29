import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P0-6（#121）：carol 抄送我的——完整表单/流转记录可见、附件可下载、只读（不能补传、不能处理）。
 * 对应 GLOSSARY「抄送」「抄送我的」「审批成员资格」与 ADR 0011（一个 gate 管读/传/处理）。
 * 演示流程的抄送规则会在 bobReview 节点抄送 carol，无需发起时手选。
 */

test('carol 能看到抄送的完整内容并下载附件，但不能处理与补传', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('抄送只读')
  const body = Buffer.from(`抄送附件内容 ${reason}`)
  const attachment = await oracle.upload('alice', {
    fileName: `cc-${reason}.txt`,
    contentType: 'text/plain',
    body,
  })
  const processId = await oracle.startApproval('alice', template, reason, {
    attachmentIds: [attachment.id],
    attachmentDraftId: attachment.draftId,
  })

  // 抄送列表的成员资格由 oracle 核对；抄送列表无排序且随轮次增长，详情直接按 id 打开。
  expect(await oracle.findMarked('carol', 'copied', reason)).toBeTruthy()
  await uiLoginExpectWork(page, 'carol')
  await page.goto(`/work/copied/${processId}`)
  await expect(page.getByRole('heading', { name: '表单信息' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '审批流转记录' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '附件' })).toBeVisible()
  await expect(page.getByText(`cc-${reason}.txt`)).toBeVisible()

  // 只读：详情不出现处理动作，也不出现补传入口。
  await expect(page.getByRole('button', { name: '通过' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '拒绝' })).toHaveCount(0)
  await expect(page.getByText('添加附件')).toHaveCount(0)

  // 附件可下载，内容与上传一致。
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载' }).first().click()
  const file = await download
  const path = await file.path()
  expect(path).toBeTruthy()
  const { readFileSync } = await import('node:fs')
  expect(readFileSync(path!, 'utf-8')).toBe(body.toString('utf-8'))

  // oracle：carol 的成员资格是只读。
  const detail = await oracle.show('carol', processId)
  expect(detail.isApprove).toBe(false)
  const list = await oracle.attachments('carol', processId)
  expect(list.canUpload).toBe(false)
})
