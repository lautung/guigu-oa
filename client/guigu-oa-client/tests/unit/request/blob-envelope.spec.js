import axios from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import request, { configureRequest } from '@/request/request'
import { PermissionRequestFailure } from '@/request/request-failure'
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

afterEach(() => {
  axios.defaults.adapter = originalAdapter
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
