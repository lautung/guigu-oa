import { test, expect } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import {
  fillDemoForm,
  openWorkList,
  submitStart,
  uiLoginExpectWork,
  uploadStartAttachment,
} from './helpers/ui'

/**
 * P0-3（#121）：alice 按已发布模板发起含附件的请假审批，已发起列表可见。
 * 对应 GLOSSARY「发起审批」「附件」（附件独立于表单保存、独立登记）与 ADR 0002。
 */

test('alice 发起含附件的审批并出现在已发起', async ({ page, oracle }) => {
  const template = await demoTemplate(oracle)
  const reason = marker('发起含附件')

  await uiLoginExpectWork(page, 'alice')
  await openWorkList(page, '发起审批')
  await page.locator('.template-card', { hasText: template.name }).click()
  await expect(page.getByRole('heading', { name: template.name, level: 2 })).toBeVisible()

  const body = Buffer.from(`栈测试附件 ${reason}`)
  await uploadStartAttachment(page, {
    name: `stk-${reason}.txt`,
    contentType: 'text/plain',
    body,
  })

  await fillDemoForm(page, { reason, days: '2' })
  await submitStart(page)

  const record = await oracle.findMarked('alice', 'started', reason)
  expect(record, 'oracle：已发起列表应能按标记找到这条审批').toBeTruthy()
  expect(record?.status).toBe(1)

  const list = await oracle.attachments('alice', record!.id)
  expect(list.attachments.some((item) => item.fileName === `stk-${reason}.txt`)).toBe(true)
  expect(list.canUpload).toBe(true)
})
