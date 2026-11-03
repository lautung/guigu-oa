import { test, expect } from './helpers/fixtures'
import { bpmnXml, buildZip, marker, runId } from './helpers/stack'
import { confirmMessageBox, rowAcrossPages, uiLoginExpectWork } from './helpers/ui'

/**
 * P1-1（#121）：模板发布 / 编辑——未发布可改可删，发布后冻结。
 * 对应 GLOSSARY「未发布」「已发布」「编辑审批模板」与 ADR 0003/0005。
 *
 * 模板经 API 造数为未发布；流程 zip 在编辑向导里经真实 UI 上传（第 3 步）。
 * 已发布模板无法删除（ADR 0005），每轮运行留下一个 runId 命名的已发布模板，
 * 这是数据策略里唯一不清理的残留。
 */

test('未发布模板可改可删，发布后冻结', async ({ page, oracle }) => {
  const admin = 'admin'
  const typeId = await oracle.processTypeId(admin, '演示审批')

  // 造两条未发布模板：一条走「可改 + 发布后冻结」，一条走「可删」。
  const nameA = marker('模板改')
  const idA = await oracle.createUnpublishedTemplate(admin, {
    name: nameA,
    processTypeId: typeId,
    description: '栈测试发布用',
  })
  const nameB = marker('模板删')
  const idB = await oracle.createUnpublishedTemplate(admin, {
    name: nameB,
    processTypeId: typeId,
    description: '栈测试删除用',
  })

  await uiLoginExpectWork(page, admin)
  await page.goto('/system/processSet/processTemplate')
  await expect(page.getByRole('heading', { name: '审批模板', level: 2 })).toBeVisible()

  // 未发布：可改（打开向导改名称、经 UI 上传流程 zip 并提交保存）。
  const rowA = await rowMustExist(page, nameA)
  await expect(rowA).toContainText('未发布')
  await rowA.getByRole('button', { name: '编辑' }).click()
  await expect(page.locator('#template-name')).toHaveValue(nameA)
  await page.locator('#template-name').fill(`${nameA}改`)
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  // 服务端以 zip 文件名（去扩展名）派生流程 key，发布时校验 BPMN 的 process id 与之一致
  //（ProcessDefinitionStorage.store + deployVerified），所以 zip 名必须等于 BPMN 的 process id。
  const processKey = `stk${runId()}ap`
  const zip = buildZip([{ name: 'stack-approval.bpmn20.xml', data: bpmnXml(processKey) }])
  await page.locator('#template-zip').setInputFiles({
    name: `${processKey}.zip`,
    mimeType: 'application/zip',
    buffer: zip,
  })
  await expect(page.getByText('当前采用的流程 zip')).toBeVisible()
  await page.getByRole('button', { name: '提交保存' }).click()
  await expect(page).toHaveURL(/\/system\/processSet\/processTemplate$/)
  const renamed = await rowMustExist(page, `${nameA}改`)
  await expect(renamed).toContainText('未发布')

  // 发布后冻结：列表行状态翻转、动作消失，API 改删都被拒。
  // （断言行状态而不是 toast：toast 存活 3 秒，与点击时机有竞态；行状态才是发布的事实。）
  await renamed.getByRole('button', { name: '发布' }).click()
  const published = await expectRowState(page, `${nameA}改`, '已发布')
  await expect(published.getByRole('button', { name: '编辑' })).toHaveCount(0)
  await expect(published.getByRole('button', { name: '删除' })).toHaveCount(0)
  await expect(published.getByRole('button', { name: '发布' })).toHaveCount(0)

  expect(
    await oracle.codeOf(admin, 'put', '/admin/process/processTemplate/update', {
      ...(await oracle.getTemplate(admin, idA)),
      name: `${nameA}改第二次`,
    }),
  ).not.toBe(200)
  expect(
    await oracle.codeOf(admin, 'delete', `/admin/process/processTemplate/remove/${idA}`),
  ).not.toBe(200)

  // 未发布：可删。逻辑删除后的契约：get 仍返回 200，但 data 为 null。
  const rowB = await rowMustExist(page, nameB)
  await rowB.getByRole('button', { name: '删除' }).click()
  await confirmMessageBox(page)
  await expect(page.locator('.el-table__row', { hasText: nameB })).toHaveCount(0)
  const afterDelete = await oracle.raw(
    'get',
    `/admin/process/processTemplate/get/${idB}`,
    undefined,
    admin,
  )
  expect(afterDelete.code).toBe(200)
  expect(afterDelete.data ?? null).toBeNull()
})

async function rowMustExist(page: import('@playwright/test').Page, name: string) {
  const row = await rowAcrossPages(page, name)
  expect(row, `列表里应能翻页找到模板 ${name}`).toBeTruthy()
  return row!
}

/** 等某行翻到目标状态（发布是异步写回列表，给轮询窗口）。 */
async function expectRowState(page: import('@playwright/test').Page, name: string, state: string) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const row = await rowAcrossPages(page, name)
    if (row && (await row.innerText()).includes(state)) return row
    await page.waitForTimeout(500)
  }
  const row = await rowMustExist(page, name)
  await expect(row).toContainText(state)
  return row
}
