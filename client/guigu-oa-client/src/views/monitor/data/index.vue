<template>
  <div class="config-page monitor-page">
    <div class="page-header">
      <h2 class="page-title">数据监控</h2>
      <span v-if="thresholdLabel" class="threshold-label">{{ thresholdLabel }}</span>
      <el-button class="refresh-button" :loading="status === 'loading'" @click="load"
        >刷新</el-button
      >
    </div>

    <ListLoadError v-if="status === 'failed'" status="failed" :error="error" @retry="load" />

    <div v-if="view" class="monitor-data-body">
      <section class="metric-card">
        <h3 class="metric-title">Hikari</h3>
        <template v-if="view.hikari.available">
          <dl class="metric-lines">
            <div v-for="line in view.hikari.lines" :key="line.label" class="metric-line">
              <dt>{{ line.label }}</dt>
              <dd>{{ line.value }}</dd>
            </div>
          </dl>
        </template>
        <p v-else class="metric-unavailable">不可用</p>
      </section>

      <section class="metric-card slow-sql-card">
        <h3 class="metric-title">慢 SQL</h3>
        <template v-if="view.slowSqlEnabled">
          <el-table :data="view.rows" row-key="key" stripe border>
            <el-table-column type="expand">
              <template #default="expanded">
                <pre class="slow-sql-full">{{ expanded.row.sql }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="时间" min-width="170" />
            <el-table-column
              prop="statementId"
              label="语句 id"
              min-width="220"
              show-overflow-tooltip
            />
            <el-table-column prop="elapsed" label="耗时" width="100" align="right" />
            <el-table-column prop="rowsText" label="行数" width="90" align="right" />
            <el-table-column prop="sqlPreview" label="SQL" min-width="320" show-overflow-tooltip />
          </el-table>
        </template>
        <el-empty v-else description="采集未开启" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import { dataMetricsRequest, presentMonitorData, type MonitorDataView } from '@/config/monitor-data'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { MonitorDataSnapshot } from '@/types/domain'

defineOptions({ name: 'MonitorDataPage' })

const userStore = useUserStore()
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')

/** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
const scope = openRequestScope(recognition, { notifyNormalErrors: false })

const status = shallowRef<'loading' | 'ready' | 'failed'>('loading')
const error = shallowRef('')
const data = shallowRef<MonitorDataView | null>(null)
/** 已拿到过一次响应：之后的刷新失败保留旧内容，只叠加错误行。 */
const loaded = shallowRef(false)
let sequence = 0

const view = computed(() => (loaded.value ? data.value : null))
const thresholdLabel = computed(() => data.value?.thresholdLabel || '')

function failureMessage(failure: unknown): string {
  return failure && typeof failure === 'object' && 'message' in failure
    ? String((failure as { message?: unknown }).message || '')
    : ''
}

async function load(): Promise<void> {
  const number = ++sequence
  status.value = 'loading'
  error.value = ''

  try {
    const response = await scope.request<MonitorDataSnapshot | null>(dataMetricsRequest())
    if (number !== sequence || !scope.current()) return
    data.value = presentMonitorData(response.data ?? null)
    loaded.value = true
    status.value = 'ready'
  } catch (failure) {
    if (number !== sequence || !scope.current()) return
    status.value = 'failed'
    error.value = failureMessage(failure)
  }
}

onMounted(() => {
  void load()
})
onBeforeUnmount(() => {
  sequence += 1
})
</script>

<style lang="scss" scoped>
.monitor-page {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.threshold-label {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.monitor-data-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metric-card {
  margin: 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.metric-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.metric-lines {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px 24px;
}

.metric-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;

  dt {
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 0;
    color: var(--el-text-color-primary);
    text-align: right;
    word-break: break-all;
  }
}

.metric-unavailable {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.slow-sql-card {
  overflow-x: auto;
}

.slow-sql-full {
  margin: 0;
  padding: 8px 12px;
  font-family: var(--el-font-family, monospace);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-primary);
}
</style>
