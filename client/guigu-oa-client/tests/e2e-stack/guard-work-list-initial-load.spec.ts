import { test } from './helpers/fixtures'
import { demoTemplate } from './helpers/oracle'
import { marker } from './helpers/stack'
import { expectWorkItemVisible, openWorkList, uiLoginExpectWork } from './helpers/ui'

/**
 * 回归守卫（#169）：办事列表首轮扫描必须等初始加载完成。
 * page-title 先于数据渲染，loading 中无行、无分页器、无空态——首轮裸扫会立即放弃
 * （本地快 runner 复现不了，GH 慢 runner 间歇红，Regression 门 4/6 连红的失败家族）。
 * 这里把所有 find* 响应人为延迟，把竞态在本地变成确定性红：修前必红、修后必绿。
 */

const FIND_DELAY_MS = 1_500

test('慢列表加载下首轮扫描仍能找到目标行', async ({ page, oracle }) => {
  await page.route(/\/admin\/process\/find/, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, FIND_DELAY_MS))
    await route.continue()
  })

  const template = await demoTemplate(oracle)
  const reason = marker('慢加载')
  const processId = await oracle.startApproval('alice', template, reason)
  await oracle.approve('bob', processId, 1)

  await uiLoginExpectWork(page, 'bob')
  await openWorkList(page, '已处理')
  await expectWorkItemVisible(page, reason)
})
