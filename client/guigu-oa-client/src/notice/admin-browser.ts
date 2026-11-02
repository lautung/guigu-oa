import { ElMessage, ElMessageBox } from 'element-plus'
import type { Router } from 'vue-router'

import {
  createNotice,
  getNotice,
  publishNotice,
  removeNotice,
  updateNotice,
  withdrawNotice,
} from '@/notice/api'
import { createNoticeEditor } from '@/notice/admin'
import { PATHS, noticeEditPath } from '@/router/paths'

/** 公告编辑页的浏览器接线：HTTP、确认框、提示与路由都收在页面之外。 */
export function createNoticeEditorPage(router: Router) {
  return createNoticeEditor({
    load: getNotice,
    create: createNotice,
    update: updateNotice,
    publish: publishNotice,
    withdraw: withdrawNotice,
    remove: removeNotice,
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
    notify: (message, kind) => {
      ElMessage({ message, type: kind, duration: 3000 })
    },
    navigate: (path) => {
      void router.push(path)
    },
    replace: (path) => {
      void router.replace(path)
    },
    editorPath: (id) => noticeEditPath(id),
    listPath: PATHS.SYSTEM_NOTICE,
  })
}
