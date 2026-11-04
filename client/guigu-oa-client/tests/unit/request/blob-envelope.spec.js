import axios, { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'

import request, { configureRequest } from '@/request/request'
import { PermissionRequestFailure, RequestFailure } from '@/request/request-failure'
import { fakeRecognition } from '../support/harness'

const originalAdapter = axios.defaults.adapter

/** 导出/模板下载走 blob，但 gate 拒绝是 HTTP 200 + JSON 信封（#259）。 */
function harness(body, contentType) {
  const notifications = []
  const recognition = fakeRecognition({
    reject: (_context, error, notifyNormal) => {
      if (notifyNormal) notifications.push(error.message)
      throw error
    },
  })
  axios.defaults.adapter = async (config) => ({
    data: body,
    status: 200,
    statusText: 'OK',
    headers: { 'content-type': contentType },
    config,
  })
  configureRequest(recognition)
  return { notifications }
}

function exportRequest() {
  return { method: 'get', url: '/admin/system/sysUser/export', responseType: 'blob' }
}

function ok(data) {
  return { data, status: 200, statusText: 'OK', headers: { 'content-type': 'application/json' } }
}

/**
 * 4xx 错误响应同样被 axios 包成 Blob（#259 二轮）：首个业务请求抛带响应体的
 * AxiosError，续期 refresh 与重放各自返回受控响应。
 */
function errorHarness({ status, body, contentType }) {
  const notifications = []
  const refreshes = []
  const tokens = { token: 'old-access', refreshToken: 'refresh' }
  let businessCalls = 0
  const recognition = fakeRecognition({
    credentials: () => tokens,
    commitRefresh: vi.fn((_context, next) => Object.assign(tokens, next)),
    reject: (_context, error, notifyNormal) => {
      if (notifyNormal) notifications.push(error.message)
      throw error
    },
  })
  axios.defaults.adapter = async (config) => {
    const url = String(config.url || '')
    if (url.includes('/admin/system/index/refresh')) {
      refreshes.push(config)
      return ok({ code: 200, data: { token: 'new-access', refreshToken: 'new-refresh' } })
    }
    businessCalls += 1
    if (businessCalls === 1) {
      throw new AxiosError('http ' + status, 'ERR_BAD_REQUEST', config, null, {
        status,
        data: body,
        headers: { 'content-type': contentType },
        config,
      })
    }
    const replay = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    return { data: replay, status: 200, statusText: 'OK', headers: {}, config }
  }
  configureRequest(recognition)
  return { notifications, refreshes, recognition, businessCalls: () => businessCalls }
}

afterEach(() => {
  axios.defaults.adapter = originalAdapter
})

describe('blob 错误响应体的信封识别（4xx）', () => {
  it('401 + JSON 208 触发续期并用新 token 重放', async () => {
    const body = new Blob([JSON.stringify({ code: 208, message: '令牌过期' })], {
      type: 'application/json',
    })
    const h = errorHarness({ status: 401, body, contentType: 'application/json' })

    await expect(request(exportRequest())).resolves.toBeInstanceOf(Blob)
    expect(h.refreshes).toHaveLength(1)
    expect(h.recognition.commitRefresh).toHaveBeenCalledTimes(1)
    expect(h.businessCalls()).toBe(2)
    expect(h.notifications).toEqual([])
  })

  it('4xx + JSON 205 按权限失败分类并只提示一次', async () => {
    const body = new Blob([JSON.stringify({ code: 205, message: '没有导出权限' })], {
      type: 'application/json',
    })
    const h = errorHarness({ status: 403, body, contentType: 'application/json' })

    const failure = request(exportRequest())
    await expect(failure).rejects.toBeInstanceOf(PermissionRequestFailure)
    await expect(failure).rejects.toMatchObject({ message: '没有导出权限' })
    expect(h.refreshes).toHaveLength(0)
    expect(h.notifications).toEqual(['没有导出权限'])
  })

  it('真实二进制 4xx 仍按传输失败处理', async () => {
    const body = new Blob(['not-json'], { type: 'application/octet-stream' })
    const h = errorHarness({ status: 400, body, contentType: 'application/octet-stream' })

    const failure = request(exportRequest())
    await expect(failure).rejects.toBeInstanceOf(RequestFailure)
    await expect(failure).rejects.toMatchObject({
      kind: 'retryable',
      detail: { source: 'http', httpStatus: 400 },
    })
    expect(h.refreshes).toHaveLength(0)
    expect(h.notifications).toHaveLength(1)
  })
})

describe('blob 响应的信封识别', () => {
  it('真正的二进制直通 Blob', async () => {
    const blob = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const h = harness(blob, blob.type)

    await expect(request(exportRequest())).resolves.toBe(blob)
    expect(h.notifications).toEqual([])
  })

  it('JSON 信封 205 按权限失败分类并只提示一次', async () => {
    const body = new Blob([JSON.stringify({ code: 205, message: '没有操作权限' })], {
      type: 'application/json',
    })
    const h = harness(body, 'application/json;charset=UTF-8')

    await expect(request(exportRequest())).rejects.toBeInstanceOf(PermissionRequestFailure)
    expect(h.notifications).toEqual(['没有操作权限'])
  })

  it('JSON 信封 201 按业务失败分类，不再返回假 xlsx', async () => {
    const body = new Blob([JSON.stringify({ code: 201, message: '导出失败' })], {
      type: 'application/json',
    })
    const h = harness(body, 'application/json')

    await expect(request(exportRequest())).rejects.toMatchObject({ message: '导出失败' })
    expect(h.notifications).toEqual(['导出失败'])
  })

  it('JSON Content-Type 但 body 不是信封时保持二进制直通', async () => {
    const body = new Blob(['not-an-envelope'], { type: 'application/json' })
    const h = harness(body, 'application/json')

    await expect(request(exportRequest())).resolves.toBe(body)
    expect(h.notifications).toEqual([])
  })
})
