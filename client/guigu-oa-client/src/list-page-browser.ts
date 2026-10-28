import { ElMessage, ElMessageBox } from 'element-plus'
import type { ListPageEffects } from './list-page'

export function listPageEffects(): ListPageEffects {
  return {
    notify: (message, kind) => {
      ElMessage({ message, type: kind, duration: 3000 })
    },
    confirm: async (message) => {
      try {
        await ElMessageBox.confirm(message, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
        return true
      } catch {
        return false
      }
    },
  }
}
