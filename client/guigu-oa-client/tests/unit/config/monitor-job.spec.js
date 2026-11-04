import { describe, expect, it } from 'vitest'

import {
  CRON_HINTS,
  DEFAULT_LOG_LIMIT,
  MONITOR_JOB_PERMISSIONS,
  formatDurationMs,
  formRules,
  jobFormOf,
  jobLogsRequest,
  jobUpdatePayloadOf,
  jobsRequest,
  presentJobLogRow,
  presentJobRow,
  runJobRequest,
  updateJobRequest,
} from '@/config/monitor-job'

const jobRecord = {
  id: 3,
  name: '演示：示例日志任务',
  beanName: 'demoLogMonitorJob',
  cron: '0 0/10 * * * ?',
  enabled: 0,
  concurrency: 0,
  remark: '演示用',
  available: true,
  lastTriggerType: 2,
  lastStatus: 1,
  lastStartedAt: '2026-01-01 08:00:00',
  lastDurationMs: 45,
  lastMessage: 'ok',
}

describe('定时任务请求构造（#238 合同）', () => {
  it('builds the jobs list request', () => {
    expect(jobsRequest()).toEqual({ method: 'get', url: '/admin/monitor/jobs' })
  })

  it('builds the update request with the whole payload on the path id', () => {
    expect(
      updateJobRequest(7, { cron: '0 0 2 * * ?', enabled: 1, concurrency: 0, remark: '备注' }),
    ).toEqual({
      method: 'put',
      url: '/admin/monitor/jobs/7',
      data: { cron: '0 0 2 * * ?', enabled: 1, concurrency: 0, remark: '备注' },
    })
  })

  it('builds the run-once request', () => {
    expect(runJobRequest(9)).toEqual({ method: 'post', url: '/admin/monitor/jobs/9/run' })
  })

  it('builds the logs request with the default and custom limits', () => {
    expect(jobLogsRequest(5)).toEqual({
      method: 'get',
      url: '/admin/monitor/jobs/5/logs',
      params: { limit: DEFAULT_LOG_LIMIT },
    })
    expect(jobLogsRequest(5).params.limit).toBe(20)
    expect(jobLogsRequest(5, 100).params.limit).toBe(100)
  })
})

describe('定时任务行投影', () => {
  it('projects the row labels for a normal enabled job', () => {
    const row = presentJobRow({ ...jobRecord, enabled: 1, concurrency: 1 })

    expect(row).toMatchObject({
      id: 3,
      name: '演示：示例日志任务',
      beanName: 'demoLogMonitorJob',
      cron: '0 0/10 * * * ?',
      enabled: true,
      concurrency: 1,
      concurrencyLabel: '允许',
      remark: '演示用',
      available: true,
      lastStatus: 1,
      lastStatusLabel: '成功',
      lastStatusKind: 'success',
      lastStartedAtLabel: '2026-01-01 08:00:00',
    })
  })

  it('labels the concurrency tiers 禁止/允许 with 0 as the default', () => {
    expect(presentJobRow({ ...jobRecord, concurrency: 0 }).concurrencyLabel).toBe('禁止')
    expect(presentJobRow({ ...jobRecord, concurrency: null }).concurrencyLabel).toBe('禁止')
    expect(presentJobRow({ ...jobRecord, concurrency: 1 }).concurrencyLabel).toBe('允许')
  })

  it('labels the three last-run states as success/danger/info tags', () => {
    expect(presentJobRow({ ...jobRecord, lastStatus: 1 })).toMatchObject({
      lastStatusLabel: '成功',
      lastStatusKind: 'success',
    })
    expect(presentJobRow({ ...jobRecord, lastStatus: 2 })).toMatchObject({
      lastStatusLabel: '失败',
      lastStatusKind: 'danger',
    })
    expect(presentJobRow({ ...jobRecord, lastStatus: 3 })).toMatchObject({
      lastStatusLabel: '跳过',
      lastStatusKind: 'info',
    })
    expect(presentJobRow({ ...jobRecord, lastStatus: null, lastStartedAt: null })).toMatchObject({
      lastStatusLabel: '—',
      lastStatusKind: '',
      lastStartedAtLabel: '—',
    })
  })

  it('formats an epoch-millis fallback for the last run time', () => {
    // 2026-01-01 08:00:00 本地时区
    const stamp = new Date(2026, 0, 1, 8, 0, 0).getTime()
    expect(presentJobRow({ ...jobRecord, lastStartedAt: stamp }).lastStartedAtLabel).toBe(
      '2026-01-01 08:00:00',
    )
  })

  it('marks only available=false rows as 异常', () => {
    expect(presentJobRow({ ...jobRecord, available: false }).available).toBe(false)
    expect(presentJobRow({ ...jobRecord, available: true }).available).toBe(true)
    expect(presentJobRow({}).available).toBe(true)
  })

  it('fills every field with a placeholder instead of undefined', () => {
    const row = presentJobRow(undefined)
    expect(row.name).toBe('')
    expect(row.cron).toBe('')
    expect(row.enabled).toBe(false)
    expect(row.lastMessage).toBe('')
  })
})

describe('执行记录行投影', () => {
  it('labels the trigger types and the run status of a log row', () => {
    const row = presentJobLogRow({
      id: 11,
      jobName: '演示：示例日志任务',
      triggerType: 2,
      status: 1,
      message: '演示定时任务执行',
      startedAt: '2026-01-01 08:00:00',
      durationMs: 12,
    })

    expect(row).toMatchObject({
      triggerLabel: '手动执行',
      statusLabel: '成功',
      statusKind: 'success',
      startedAtLabel: '2026-01-01 08:00:00',
      durationLabel: '12 ms',
      failed: false,
    })
  })

  it('labels automatic runs and failed runs with the failure flag', () => {
    expect(presentJobLogRow({ triggerType: 1, status: 2, message: 'boom' })).toMatchObject({
      triggerLabel: '自动调度',
      statusLabel: '失败',
      statusKind: 'danger',
      failed: true,
    })
    expect(presentJobLogRow({ triggerType: 2, status: 3 })).toMatchObject({
      statusLabel: '跳过',
      statusKind: 'info',
      failed: false,
    })
  })

  it('keeps multiline failure messages intact for the expand row', () => {
    const message = 'java.lang.IllegalStateException: boom\n\tat com.lautung.oa.Demo.execute'
    expect(presentJobLogRow({ status: 2, message }).message).toBe(message)
  })

  it('shows placeholders for an empty log row', () => {
    expect(presentJobLogRow(undefined)).toMatchObject({
      triggerLabel: '—',
      statusLabel: '—',
      startedAtLabel: '—',
      durationLabel: '—',
      message: '',
    })
  })
})

describe('耗时格式化', () => {
  it('keeps sub-second durations in milliseconds', () => {
    expect(formatDurationMs(45)).toBe('45 ms')
    expect(formatDurationMs(0)).toBe('0 ms')
  })

  it('switches to seconds at one second and above', () => {
    expect(formatDurationMs(1000)).toBe('1.00 秒')
    expect(formatDurationMs(1250)).toBe('1.25 秒')
  })

  it('shows a placeholder for missing durations', () => {
    expect(formatDurationMs(null)).toBe('—')
    expect(formatDurationMs(undefined)).toBe('—')
  })
})

describe('编辑表单映射', () => {
  it('maps a row into the dialog form and back into the update payload', () => {
    const form = jobFormOf(presentJobRow({ ...jobRecord, enabled: 1, concurrency: 1 }))

    expect(form).toEqual({
      cron: '0 0/10 * * * ?',
      enabled: true,
      concurrency: true,
      remark: '演示用',
    })
    expect(jobUpdatePayloadOf(form)).toEqual({
      cron: '0 0/10 * * * ?',
      enabled: 1,
      concurrency: 1,
      remark: '演示用',
    })
  })

  it('trims cron and remark in the payload', () => {
    const payload = jobUpdatePayloadOf({
      cron: ' 0 0 2 * * ? ',
      enabled: false,
      concurrency: false,
      remark: ' 备注 ',
    })

    expect(payload.cron).toBe('0 0 2 * * ?')
    expect(payload.remark).toBe('备注')
    expect(payload.enabled).toBe(0)
    expect(payload.concurrency).toBe(0)
  })

  it('requires cron in the dialog form', () => {
    const rules = formRules()
    expect(rules.cron).toHaveLength(1)

    return new Promise((resolve) => {
      rules.cron[0].validator({}, '   ', (error) => {
        expect(error).toBeInstanceOf(Error)
        rules.cron[0].validator({}, '0 0 2 * * ?', (okError) => {
          expect(okError).toBeUndefined()
          resolve()
        })
      })
    })
  })
})

describe('按钮点与提示', () => {
  it('exposes the edit and run permission points, read stays ungated', () => {
    expect(MONITOR_JOB_PERMISSIONS).toEqual({
      edit: 'monitor:job:edit',
      run: 'monitor:job:run',
    })
  })

  it('ships common cron hints in the Spring six-field shape', () => {
    expect(CRON_HINTS.length).toBeGreaterThanOrEqual(5)
    for (const hint of CRON_HINTS) {
      expect(hint.expr.split(' ')).toHaveLength(6)
      expect(hint.label).toBeTruthy()
    }
  })
})
