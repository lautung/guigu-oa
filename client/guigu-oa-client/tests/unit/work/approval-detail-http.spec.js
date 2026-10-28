import { expect, it } from 'vitest'
import axios, { AxiosError } from 'axios'
import { createRecognition } from '@/recognition'
import { createRequestScope } from '@/request-scope'
import { createRequestClient } from '@/utils/request'
import { createHttpDetailAdapter } from '@/work/approval-detail-http'
import { createApprovalDetail } from '@/work/approval-detail'
import { workListPerspective } from '@/work/work-list'

function harness(
  approvalResponse,
  refreshResponse = { code: 200, data: { token: 'new', refreshToken: 'new-refresh' } },
) {
  const calls = [],
    notifications = [],
    navigation = []
  let tokens = { token: 'old', refreshToken: 'refresh' },
    entryId = 'login-1'
  const recognition = createRecognition({
    entryId: () => entryId,
    credentials: () => tokens,
    writeTokens: (value) => {
      tokens = value
    },
    clearUser: () => {
      entryId = ''
      tokens = { token: '' }
    },
    hasUser: () => true,
    writeUser: () => {},
    recovery: () => ({ status: 'idle', target: '' }),
    showRecovery: () => {},
    loadUser: async () => ({ name: 'Alice' }),
    currentPath: () => '/work/pending/A/task-A',
    goTo: (path) => navigation.push(path),
    goToLogin: () => navigation.push('/login'),
    notify: (text) => notifications.push(text),
  })
  const original = axios.defaults.adapter
  axios.defaults.adapter = async (config) => {
    calls.push(config)
    let data
    if (config.url.endsWith('/refresh')) data = refreshResponse
    else if (config.url.includes('/show/'))
      data = { code: 200, data: { process: { id: 'A', title: '审批 A' }, isApprove: true } }
    else if (config.url.includes('/attachments/'))
      data = { code: 200, data: { attachments: [], canUpload: true } }
    else
      data = await approvalResponse(calls.filter((x) => x.url.endsWith('/approve')).length, config)
    return { data, config, status: 200, statusText: 'OK', headers: {} }
  }
  const memory = new Map()
  const client = createRequestClient(recognition)
  const adapter = createHttpDetailAdapter({
    openScope: () => createRequestScope(recognition, client, { notifyNormalErrors: false }),
    entryId: () => recognition.capture().entryId,
    storage: {
      getItem: (key) => memory.get(key) ?? null,
      setItem: (key, value) => memory.set(key, value),
    },
    navigate: (path) => navigation.push(path),
    openDownload: () => {},
  })
  const opened = createApprovalDetail(adapter).open({
    processId: 'A',
    taskId: 'task-A',
    perspective: workListPerspective('pending'),
  })
  return {
    opened,
    calls,
    notifications,
    navigation,
    restore: () => {
      axios.defaults.adapter = original
    },
  }
}

it('业务208只恢复认证并最多重放一次审批，普通明确拒绝由详情呈现且不重复通知', async () => {
  const h = harness((n) =>
    n === 1 ? { code: 208, message: '认证过期' } : { code: 403, message: '任务已变化' },
  )
  try {
    await h.opened.ready
    await h.opened.decide(1)
    expect(h.calls.filter((x) => x.url.endsWith('/approve'))).toHaveLength(2)
    expect(h.calls.filter((x) => x.url.endsWith('/refresh'))).toHaveLength(1)
    expect(h.opened.state.actionError).toBe('任务已变化')
    expect(h.opened.state.recoveries).toEqual([])
    expect(h.navigation).toEqual([])
    expect(h.notifications).toEqual([])
  } finally {
    h.restore()
  }
})

it.each([
  [
    'network',
    async (_n, config) => {
      throw new AxiosError('断网', 'ERR_NETWORK', config)
    },
  ],
  [
    'unknown-http',
    async (_n, config) => {
      throw new AxiosError('HTTP 503', 'ERR_BAD_RESPONSE', config, null, {
        status: 503,
        data: { code: 201 },
      })
    },
  ],
  ['unknown-body', async () => '<html>网关错误</html>'],
  ['unknown-code', async () => ({ code: 599, message: '未知响应' })],
])('%s 保留待确认且不重放、不产生全局普通错误', async (_name, respond) => {
  const h = harness(respond)
  try {
    await h.opened.ready
    await h.opened.decide(-1)
    expect(h.opened.state.recoveries).toHaveLength(1)
    expect(h.opened.state.canSubmit).toBe(false)
    expect(h.calls.filter((x) => x.url.endsWith('/approve'))).toHaveLength(1)
    expect(h.notifications).toEqual([])
  } finally {
    h.restore()
  }
})

it.each([
  ['again-expired', { code: 200, data: { token: 'new', refreshToken: 'new-refresh' } }, 2],
  ['refresh-failed', { code: 201, message: '刷新暂时失败' }, 1],
  ['disabled', { code: 209, message: '用户停用' }, 1],
])('认证恢复 %s 最多重放一次，恢复失败不重放', async (_name, response, count) => {
  const h = harness(async () => ({ code: 208, message: '认证过期' }), response)
  try {
    await h.opened.ready
    await h.opened.decide(1)
    expect(h.calls.filter((x) => x.url.endsWith('/approve'))).toHaveLength(count)
    expect(h.calls.filter((x) => x.url.endsWith('/refresh'))).toHaveLength(1)
    if (_name !== 'refresh-failed') expect(h.navigation).toEqual(['/login'])
    else expect(h.notifications).toEqual([])
  } finally {
    h.restore()
  }
})

it('离开后的208不发提示、不恢复后重放原处理，也不抢导航', async () => {
  let complete
  const response = new Promise((resolve) => {
    complete = resolve
  })
  let reached
  const dispatched = new Promise((resolve) => {
    reached = resolve
  })
  const h = harness(() => {
    reached()
    return response
  })
  try {
    await h.opened.ready
    const sending = h.opened.decide(1)
    await dispatched
    h.opened.release()
    complete({ code: 208, message: '旧打开认证过期' })
    await sending
    expect(h.calls.filter((x) => x.url.endsWith('/approve'))).toHaveLength(1)
    expect(h.calls.filter((x) => x.url.endsWith('/refresh'))).toHaveLength(0)
    expect(h.notifications).toEqual([])
    expect(h.navigation).toEqual([])
  } finally {
    h.restore()
  }
})

it.each([201, 403])('事务回滚明确业务 %s 留在详情，刷新核实前不再处理', async (code) => {
  const h = harness(async () => ({ code, message: '本次处理未获接受' }))
  try {
    await h.opened.ready
    await h.opened.decide(1)
    expect(h.opened.state.recoveries).toEqual([])
    expect(h.opened.state.actionError).toBe('本次处理未获接受')
    expect(h.opened.state.canSubmit).toBe(false)
    expect(h.navigation).toEqual([])
    expect(h.notifications).toEqual([])
  } finally {
    h.restore()
  }
})
