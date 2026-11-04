<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">定时任务</h2>
      <span class="page-hint">任务行由启动登记产生，不提供新增与删除（ADR 0060）</span>
    </div>

    <ListLoadError :status="status" :error="error" @retry="refresh" />

    <div class="table-scroll">
      <el-table
        v-loading="status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        row-key="id"
        stripe
        border
      >
        <el-table-column label="名称" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.name }}</span>
            <el-tag v-if="!scope.row.available" class="broken-tag" type="danger" size="small"
              >异常</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column prop="beanName" label="bean" min-width="170" show-overflow-tooltip />
        <el-table-column label="cron" min-width="140">
          <template #default="scope">
            <code class="cron-text">{{ scope.row.cron }}</code>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-switch
              v-hasPermi="permissions.edit"
              :model-value="scope.row.enabled"
              :disabled="!scope.row.available"
              @change="(value) => toggleEnabled(scope.row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="concurrencyLabel" label="并发" width="80" align="center" />
        <el-table-column label="最近状态" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.lastStatusKind" :type="scope.row.lastStatusKind" size="small">{{
              scope.row.lastStatusLabel
            }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastStartedAtLabel" label="最近执行时间" min-width="160" />
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="permissions.edit"
              link
              type="primary"
              :disabled="!scope.row.available"
              @click="openEdit(scope.row)"
              >编辑</el-button
            >
            <el-button
              v-hasPermi="permissions.run"
              link
              type="primary"
              :disabled="!scope.row.available"
              @click="runOnce(scope.row)"
              >执行一次</el-button
            >
            <el-button link type="primary" @click="openLogs(scope.row)">执行记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="`编辑任务 — ${editingName}`"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="saveError"
        class="save-error"
        type="error"
        :title="saveError"
        :closable="false"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="cron" prop="cron">
          <el-input v-model="form.cron" class="cron-input" placeholder="秒 分 时 日 月 周" />
          <div class="cron-hints">
            <span v-for="hint in cronHints" :key="hint.expr" class="cron-hint">
              {{ hint.label }} <code>{{ hint.expr }}</code>
            </span>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
          <span class="switch-hint">{{ form.enabled ? '已启用' : '已停用' }}</span>
        </el-form-item>
        <el-form-item label="并发">
          <el-switch v-model="form.concurrency" />
          <span class="switch-hint">{{ form.concurrency ? '允许并发' : '禁止并发' }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="drawerVisible"
      :title="`执行记录 — ${drawerJobName}`"
      size="62%"
      :destroy-on-close="true"
    >
      <ListLoadError :status="logStatus" :error="logError" @retry="retryLogs" />
      <el-table v-loading="logStatus === 'loading'" :data="logRows" row-key="id" stripe border>
        <el-table-column type="expand">
          <template #default="scope">
            <pre v-if="scope.row.message" class="log-message-full">{{ scope.row.message }}</pre>
            <span v-else class="log-message-empty">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerLabel" label="触发方式" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.statusKind" :type="scope.row.statusKind" size="small">{{
              scope.row.statusLabel
            }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="startedAtLabel" label="开始时间" min-width="160" />
        <el-table-column prop="durationLabel" label="耗时" width="100" align="right" />
        <el-table-column label="信息" min-width="220">
          <template #default="scope">
            <div
              v-if="scope.row.message"
              class="log-message-cell"
              :class="{ failed: scope.row.failed }"
            >
              {{ scope.row.message }}
            </div>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script lang="ts">
import {
  computed,
  defineComponent,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  shallowRef,
} from 'vue'

import { runJob, updateJob } from '@/api/monitor'
import ListLoadError from '@/components/ListLoadError.vue'
import {
  CRON_HINTS,
  DEFAULT_LOG_LIMIT,
  MONITOR_JOB_PERMISSIONS,
  formRules,
  jobFormOf,
  jobUpdatePayloadOf,
  jobLogsRequest,
  jobsRequest,
  presentJobLogRow,
  presentJobRow,
  type MonitorJobForm,
  type MonitorJobLogRow,
  type MonitorJobRow,
} from '@/config/monitor-job'
import { listPageEffects } from '@/list/list-page-browser'
import { failureMessage } from '@/request/request-failure'
import { openRequestScope } from '@/request/request-scope'
import { createSnapshotRead } from '@/request/snapshot-read'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, MonitorJobLogRecord, MonitorJobRecord } from '@/types/domain'

/** el-table 插槽里的行是 DefaultRow：模板入口收窄成松形状，内部逻辑仍走投影行。 */
interface JobRowLike {
  id?: EntityId | null
  name?: string | null
  available?: boolean | null
  enabled?: boolean | null
}

export default defineComponent({
  name: 'MonitorJobPage',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')

    /** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    const effects = listPageEffects()

    const jobsRead = createSnapshotRead<MonitorJobRow[]>(scope)
    const { status, error, loaded } = jobsRead
    const rows = computed(() => jobsRead.value.value ?? [])

    const emptyText = computed(() => (loaded.value ? '暂无数据' : ''))

    function load(): Promise<void> {
      return jobsRead.load(async (requestScope) => {
        const response = await requestScope.request<MonitorJobRecord[]>(jobsRequest())
        return (response.data ?? []).map(presentJobRow)
      })
    }

    /** 列表读：手动执行后由用户稍后刷新看记录，触发本身不刷新。 */
    function refresh(): void {
      void load()
    }

    /** 启停开关（走 monitor:job:edit）：失败回滚本地开关，成功提示并刷新。 */
    async function toggleEnabled(
      record: JobRowLike,
      next: boolean | string | number,
    ): Promise<void> {
      const row = record as MonitorJobRow
      if (row?.id === '' || row?.id == null || !row.available) return
      const previous = !next
      try {
        await updateJob(row.id, { enabled: next ? 1 : 0 })
      } catch {
        // 请求层已上屏失败提示；开关回滚到原值。
        row.enabled = previous
        return
      }
      effects.notify(next ? '已启用' : '已停用', 'success')
      refresh()
    }

    const dialogVisible = shallowRef(false)
    const editingName = shallowRef('')
    const editingId = shallowRef<number | string | null>(null)
    const saving = shallowRef(false)
    const saveError = shallowRef('')
    const formRef = ref()
    const form = reactive<MonitorJobForm>({
      cron: '',
      enabled: false,
      concurrency: false,
      remark: '',
    })
    const rules = formRules()
    const cronHints = CRON_HINTS

    function openEdit(record: JobRowLike): void {
      const row = record as MonitorJobRow
      if (!row.available) return
      editingId.value = row.id
      editingName.value = row.name
      Object.assign(form, jobFormOf(row))
      saveError.value = ''
      dialogVisible.value = true
    }

    /** 保存：本地只判必填，cron 合法性由服务端 CronExpression 权威判定；201 文案回显在弹窗内。 */
    async function saveEdit(): Promise<void> {
      if (formRef.value) {
        try {
          await formRef.value.validate()
        } catch {
          return
        }
      }
      if (editingId.value == null || editingId.value === '') return
      saving.value = true
      saveError.value = ''
      try {
        await updateJob(editingId.value, jobUpdatePayloadOf(form))
      } catch (failure) {
        saveError.value = failureMessage(failure) || '保存失败，请重试'
        return
      } finally {
        saving.value = false
      }
      effects.notify('已保存', 'success')
      dialogVisible.value = false
      refresh()
    }

    /** 手动执行（monitor:job:run）：异步触发立即返回，disabled 任务也放行（ADR 0060）。 */
    async function runOnce(record: JobRowLike): Promise<void> {
      const row = record as MonitorJobRow
      if (!row.available) return
      const confirmed = await effects.confirm(
        `确定立即执行一次「${row.name}」? 任务将异步触发，稍后刷新或打开执行记录查看结果。`,
      )
      if (!confirmed) return
      try {
        await runJob(row.id)
      } catch {
        // 请求层已上屏失败提示。
        return
      }
      effects.notify('已触发', 'success')
    }

    const drawerVisible = shallowRef(false)
    const drawerJobName = shallowRef('')
    const drawerJobId = shallowRef<number | string | null>(null)
    const logsRead = createSnapshotRead<MonitorJobLogRow[]>(scope)
    const { status: logStatus, error: logError } = logsRead
    const logRows = computed(() => logsRead.value.value ?? [])

    function loadLogs(jobId: number | string): Promise<void> {
      return logsRead.load(async (requestScope) => {
        const response = await requestScope.request<MonitorJobLogRecord[]>(
          jobLogsRequest(jobId, DEFAULT_LOG_LIMIT),
        )
        return (response.data ?? []).map(presentJobLogRow)
      })
    }

    /** 打开抽屉重读最近 20 条；快速换行时 sequence 丢迟到的旧窗口。 */
    async function openLogs(record: JobRowLike): Promise<void> {
      const row = record as MonitorJobRow
      drawerJobId.value = row.id
      drawerJobName.value = row.name
      drawerVisible.value = true
      await loadLogs(row.id)
    }

    function retryLogs(): void {
      if (drawerJobId.value == null || drawerJobId.value === '') return
      void loadLogs(drawerJobId.value)
    }

    onMounted(() => {
      refresh()
    })
    onBeforeUnmount(() => {
      jobsRead.release()
      logsRead.release()
    })

    return {
      permissions: MONITOR_JOB_PERMISSIONS,
      rows,
      status,
      error,
      emptyText,
      refresh,
      toggleEnabled,
      dialogVisible,
      editingName,
      saving,
      saveError,
      formRef,
      form,
      rules,
      cronHints,
      openEdit,
      saveEdit,
      runOnce,
      drawerVisible,
      drawerJobName,
      logRows,
      logStatus,
      logError,
      openLogs,
      retryLogs,
    }
  },
})
</script>

<style lang="scss" scoped>
.config-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.page-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.broken-tag {
  margin-left: 8px;
}

.cron-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.save-error {
  margin-bottom: 12px;
}

.cron-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.cron-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.cron-hint code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.switch-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.log-message-cell {
  white-space: pre-line;
  word-break: break-all;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;

  &.failed {
    color: var(--el-color-danger);
  }
}

.log-message-full {
  margin: 0;
  padding: 8px 12px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.log-message-empty {
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .config-page {
    padding: 16px;
  }

  .table-scroll :deep(.el-table) {
    min-width: 960px;
  }

  .cron-hints {
    flex-direction: column;
  }
}
</style>
