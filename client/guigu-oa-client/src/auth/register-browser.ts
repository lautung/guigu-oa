import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { loadRegisterSwitch, submitRegister } from '@/api/register'
import { PATHS } from '@/router/paths'
import { createRegister } from './register'

const NOTICE_DURATION = 3000

/**
 * `/register` 页面的浏览器接线（#253）：开关探测、注册请求、提示与「成功跳登录并预填用户名」。
 * 路由跳转留在适配器里，状态机本身不依赖 vue-router，单测可以直接驱动。
 */
export function createRegisterPage() {
  const router = useRouter()

  return createRegister({
    loadSwitch: loadRegisterSwitch,
    submit: submitRegister,
    notify: (message, type = 'success') => ElMessage({ message, type, duration: NOTICE_DURATION }),
    goToLogin: async (username) => {
      await router.push({ path: PATHS.LOGIN, query: { username } })
    },
  })
}
