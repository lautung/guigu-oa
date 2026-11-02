import axios, { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import { useUserStore } from '@/store/modules/user'
import { configureRequest } from '@/request/request'

/** 审批表单值的存储型 XSS 载荷（#142）：页面必须把它显示成字符，不能生成元素。 */
export const XSS_PAYLOAD = '<img src=x onerror="window.__secXss=1">'

/** jsdom lacks ResizeObserver; Element Plus components expect it when mounted for real. */
export function ensureDomApis() {
  if (typeof globalThis.ResizeObserver === 'undefined') {
    globalThis.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }
}

export function fakeRecognition(overrides = {}) {
  const context = { generation: 1, entryId: 'entry-1' }
  return {
    capture: () => context,
    assertCurrent: () => {},
    credentials: () => ({ token: 'token', refreshToken: 'refresh' }),
    commitRefresh: () => {},
    reject: (_context, error) => {
      throw error
    },
    recognize: async () => ({ name: 'Alice', avatar: '', configMenus: [] }),
    establish: () => {},
    resume: async () => {},
    exit: () => {},
    forget: () => {},
    ...overrides,
  }
}

export function setupSession(options = {}) {
  const recognition = options.recognition || fakeRecognition()
  const pinia = createPinia()
  setActivePinia(pinia)
  const user = useUserStore(pinia)
  user.token = options.token === undefined ? 'token' : options.token
  user.menus = options.menus || []
  user.name = options.name || ''
  user.avatar = options.avatar || ''
  user.recognition = recognition
  configureRequest(recognition)
  return { pinia, user, recognition }
}

export function createTestRouter(routes = []) {
  return createRouter({ history: createMemoryHistory(), routes })
}

export const elementStubs = {
  'el-alert': true,
  'el-button': true,
  'el-card': true,
  'el-checkbox': true,
  'el-checkbox-group': true,
  'el-color-picker': true,
  'el-date-picker': true,
  'el-descriptions': true,
  'el-descriptions-item': true,
  'el-dialog': true,
  'el-divider': true,
  'el-empty': true,
  'el-form': true,
  'el-form-item': true,
  'el-icon': true,
  'el-image': true,
  'el-input': true,
  'el-input-number': true,
  'el-option': true,
  'el-pagination': true,
  'el-radio': true,
  'el-radio-group': true,
  'el-select': true,
  'el-step': true,
  'el-steps': true,
  'el-switch': true,
  'el-table': true,
  'el-table-column': true,
  'el-tag': true,
  'el-timeline': true,
  'el-timeline-item': true,
  'el-tooltip': true,
  'el-tree': true,
  'el-upload': true,
  'form-create': true,
  'fc-designer': true,
}

export function mountOptions(pinia, router) {
  return {
    global: {
      plugins: router ? [pinia, router] : [pinia],
      stubs: elementStubs,
      directives: { loading: {} },
    },
  }
}

/**
 * Responder 返回响应体按 HTTP 200 送达；抛 AxiosError 原样冒泡，抛其他错误包装成
 * ERR_NETWORK（axios adapter 语义）——错误响应形状（#145/#155）由此在共享工具一处拥有。
 */
export function installHttp(responder) {
  const original = axios.defaults.adapter
  const calls = []
  axios.defaults.adapter = async (config) => {
    calls.push(config)
    try {
      const body = await responder(config, calls.length)
      return { data: body, status: 200, statusText: 'OK', headers: {}, config }
    } catch (error) {
      throw error instanceof AxiosError
        ? error
        : new AxiosError(error.message, 'ERR_NETWORK', config)
    }
  }
  return {
    calls,
    restore: () => {
      axios.defaults.adapter = original
    },
  }
}

export function ok(data) {
  return { code: 200, data, message: '' }
}

export function paged(records, total = records.length) {
  return ok({ records, total, pages: 1 })
}
