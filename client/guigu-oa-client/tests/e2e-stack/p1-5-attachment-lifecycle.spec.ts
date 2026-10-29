import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import { openWorkList, uiLoginExpectWork, uploadStartAttachment } from './helpers/ui'

/**
 * P1-5（#121）：附件生命周期——发起前可删、发起后不可删、审批结束保留。
 * 对应 GLOSSARY「附件」与 ADR 0002/0011。附件上传走真实 MinIO。
 */

test('附件发起前可在发起页删除', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('发起前删除')
  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '发起审批')
  await page.locator('.template-card', { hasText: template.name }).click()
  await expect(page.getByRole('heading', { name: template.name, level: 2 })).toBeVisible()

  const first = Buffer.from(`将被删除 ${reason}`)
  await uploadStartAttachment(page, {
    name: `removed-${reason}.txt`,
    contentType: 'text/plain',
    body: first,
  })
  await page.locator('.attachment-section').getByRole('button', { name: '删除' }).click()
  await expect(page.locator('.attachment-section').getByText(`removed-${reason}.txt`)).toBeHidden()
  await expect(page.getByText('暂无附件')).toBeVisible()
})

test('附件发起后不可删、审批结束保留且可下载', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('附件生命周期')
  const body = Buffer.from(`生命周期附件 ${reason}`)
  const fileName = `life-${reason}.txt`

  const uploaded = await oracle.upload('alice', { fileName, contentType: 'text/plain', body })
  const processId = await oracle.startApproval('alice', template, reason, {
    attachmentIds: [uploaded.id],
    attachmentDraftId: uploaded.draftId,
  })

  // 发起后不可删：详情页没有删除动作，API 也拒绝（已发起列表无排序、随轮次增长，直接按 id 打开详情）。
  await uiLoginExpectWork(page, 'alice')
  await page.goto(`/work/started/${processId}`)
  await expect(page.getByRole('heading', { name: '附件' })).toBeVisible()
  await expect(page.getByText(fileName)).toBeVisible()
  await expect(
    page.locator('.attachment-section').getByRole('button', { name: '删除' }),
  ).toHaveCount(0)
  const list = await oracle.attachments('alice', processId)
  expect(list.attachments).toHaveLength(1)
  const attachmentId = list.attachments[0].id
  expect(
    await oracle.codeOf('alice', 'delete', `/admin/attachments/pending/${attachmentId}`),
  ).not.toBe(200)

  // 审批结束后保留：bob 通过后附件仍在、可下载、内容一致。
  await oracle.approve('bob', processId, 1)
  const afterList = await oracle.attachments('alice', processId)
  expect(afterList.attachments.some((item) => item.fileName === fileName)).toBe(true)
  const downloaded = await oracle.download('alice', processId, attachmentId)
  expect(downloaded.toString('utf-8')).toBe(body.toString('utf-8'))
})
