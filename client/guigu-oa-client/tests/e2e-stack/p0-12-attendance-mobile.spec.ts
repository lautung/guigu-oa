import { test, expect } from './helpers/fixtures'
import {
  createAndSubmitLeave,
  ensureTodayWorkday,
  freeWorkday,
  punchViaUi,
} from './helpers/attendance'
import { poll } from './helpers/oracle'
import { marker } from './helpers/stack'
import { uiLoginExpectWork } from './helpers/ui'

/**
 * P0-12（#284）：手机 390 视口覆盖考勤办事两条关键路径（ADR 0073 的窄屏约束）。
 *
 * 登录 → 上班打卡 → 新建请假并提交；审批通过由桌面主路径覆盖，这里只证
 * 「手机上能打卡、能提交请假」且页面没有横向溢出。
 */
test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })

test('手机视口完成打卡与请假提交', async ({ page, oracle }) => {
  const today = await ensureTodayWorkday(oracle)
  await oracle.ensureAliceInDemoDept()
  const leaveDate = await freeWorkday(oracle, 'alice', today.date)

  await uiLoginExpectWork(page, 'alice')
  await punchViaUi(page, 'in')

  // 事假不占余额：手机路径只证「能提交」，余额扣减由桌面主路径覆盖。
  const reason = marker('手机考勤')
  await createAndSubmitLeave(page, { reason, date: leaveDate, typeName: '事假' })

  const started = await poll(20_000, 500, async () => {
    const leaves = await oracle.attendanceLeaves('alice')
    return leaves.records.find((record) => record.reason === reason)
  })
  expect(started?.approval?.processId, '手机提交应发起业务审批').toBeTruthy()

  const viewport = await page.evaluate(() => ({
    width: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
  }))
  expect(viewport.documentWidth).toBeLessThanOrEqual(viewport.width)
})
