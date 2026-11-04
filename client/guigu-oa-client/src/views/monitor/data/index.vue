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

      <section class="metric-card sql-statistics-card">
        <h3 class="metric-title">SQL 统计</h3>
        <p v-if="view.sqlStatistics.available && view.sqlStatistics.note" class="metric-note">
          {{ view.sqlStatistics.note }}
        </p>
        <p v-if="!view.sqlStatistics.available" class="metric-unavailable">不可用</p>
        <el-empty v-else-if="!view.sqlStatistics.enabled" description="采集未开启" />
        <el-table
          v-else
          :data="view.sqlStatistics.rows"
          row-key="key"
          stripe
          border
          empty-text="窗口内暂无慢 SQL"
        >
          <el-table-column
            prop="statementId"
            label="语句 id"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column prop="countText" label="次数" width="90" align="right" />
          <el-table-column prop="avgText" label="平均耗时" width="120" align="right" />
          <el-table-column prop="maxText" label="最大耗时" width="120" align="right" />
          <el-table-column prop="lastTime" label="最近一条" min-width="170" />
        </el-table>
      </section>

      <section class="metric-card pool-history-card">
        <h3 class="metric-title">连接池历史</h3>
        <p v-if="view.poolHistory.available && view.poolHistory.intervalLabel" class="metric-note">
          {{ view.poolHistory.intervalLabel }}
        </p>
        <p v-if="!view.poolHistory.available" class="metric-unavailable">不可用</p>
        <el-empty v-else-if="view.poolHistory.points.length === 0" description="暂无采样" />
        <template v-else>
          <svg
            class="pool-history-chart"
            :viewBox="`0 0 ${view.poolHistory.chart.width} ${view.poolHistory.chart.height}`"
            role="img"
            aria-label="连接池历史折线"
            preserveAspectRatio="none"
          >
            <polyline
              class="pool-line pool-line-total"
              :points="view.poolHistory.chart.total"
              fill="none"
            />
            <polyline
              class="pool-line pool-line-active"
              :points="view.poolHistory.chart.active"
              fill="none"
            />
            <polyline
              class="pool-line pool-line-idle"
              :points="view.poolHistory.chart.idle"
              fill="none"
            />
            <polyline
              class="pool-line pool-line-waiting"
              :points="view.poolHistory.chart.waiting"
              fill="none"
            />
          </svg>
          <div class="pool-history-legend">
            <span class="legend-total">总数</span>
            <span class="legend-active">活跃</span>
            <span class="legend-idle">空闲</span>
            <span class="legend-waiting">等待</span>
          </div>
          <p class="pool-history-latest">
            最新：活跃 {{ view.poolHistory.latest?.activeText }} · 空闲
            {{ view.poolHistory.latest?.idleText }} · 总数
            {{ view.poolHistory.latest?.totalText }} · 等待
            {{ view.poolHistory.latest?.threadsAwaitingText }}
          </p>
        </template>
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
import { computed, onBeforeUnmount, onMounted } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import { dataMetricsRequest, presentMonitorData, type MonitorDataView } from '@/config/monitor-data'
import { openRequestScope } from '@/request/request-scope'
import { createSnapshotRead } from '@/request/snapshot-read'
import { useUserStore } from '@/store/modules/user'
import type { MonitorDataSnapshot } from '@/types/domain'

defineOptions({ name: 'MonitorDataPage' })

const userStore = useUserStore()
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')

/** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
const scope = openRequestScope(recognition, { notifyNormalErrors: false })
const snapshotRead = createSnapshotRead<MonitorDataView>(scope)
const { status, error, value: data, loaded } = snapshotRead

const view = computed(() => (loaded.value ? data.value : null))
const thresholdLabel = computed(() => data.value?.thresholdLabel || '')

function load(): Promise<void> {
  return snapshotRead.load(async (requestScope) => {
    const response = await requestScope.request<MonitorDataSnapshot | null>(dataMetricsRequest())
    return presentMonitorData(response.data ?? null)
  })
}

onMounted(() => {
  void load()
})
onBeforeUnmount(() => snapshotRead.release())
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

.metric-note {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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

.sql-statistics-card,
.slow-sql-card {
  overflow-x: auto;
}

.pool-history-chart {
  display: block;
  width: 100%;
  height: 140px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pool-line {
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
}

.pool-line-total {
  stroke: var(--el-color-primary);
}

.pool-line-active {
  stroke: var(--el-color-danger);
}

.pool-line-idle {
  stroke: var(--el-color-success);
}

.pool-line-waiting {
  stroke: var(--el-color-warning);
}

.pool-history-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  span::before {
    content: '';
    display: inline-block;
    width: 10px;
    height: 2px;
    margin-right: 4px;
    vertical-align: middle;
    background: currentColor;
  }
}

.legend-total {
  color: var(--el-color-primary);
}

.legend-active {
  color: var(--el-color-danger);
}

.legend-idle {
  color: var(--el-color-success);
}

.legend-waiting {
  color: var(--el-color-warning);
}

.pool-history-latest {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
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
