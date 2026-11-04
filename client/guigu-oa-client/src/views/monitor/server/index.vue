<template>
  <div class="config-page monitor-page">
    <div class="page-header">
      <h2 class="page-title">服务监控</h2>
      <el-button class="refresh-button" :loading="status === 'loading'" @click="load"
        >刷新</el-button
      >
    </div>

    <ListLoadError v-if="status === 'failed'" status="failed" :error="error" @retry="load" />

    <div v-if="view" v-loading="status === 'loading' && !loaded" class="metric-grid">
      <section v-for="card in cards" :key="card.key" class="metric-card">
        <h3 class="metric-title">{{ card.title }}</h3>
        <template v-if="card.section.available">
          <dl class="metric-lines">
            <div v-for="line in card.section.lines" :key="line.label" class="metric-line">
              <dt>{{ line.label }}</dt>
              <dd>{{ line.value }}</dd>
            </div>
          </dl>
          <div v-for="disk in card.section.disks" :key="disk.mountPoint" class="disk-row">
            <div class="disk-head">
              <span class="disk-mount">{{ disk.mountPoint }}</span>
              <span class="disk-text"
                >已用 {{ disk.used }} / 共 {{ disk.total }}（{{ disk.percentLabel }}）</span
              >
            </div>
            <el-progress
              :percentage="disk.percent"
              :stroke-width="10"
              :status="disk.percent >= 90 ? 'exception' : undefined"
            />
          </div>
        </template>
        <p v-else class="metric-unavailable">不可用</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import {
  presentServerMetrics,
  serverMetricsRequest,
  type ServerSectionKey,
} from '@/config/monitor-server'
import { openRequestScope } from '@/request/request-scope'
import { createSnapshotRead } from '@/request/snapshot-read'
import { useUserStore } from '@/store/modules/user'
import type { ServerMetrics } from '@/types/domain'

defineOptions({ name: 'MonitorServerPage' })

const userStore = useUserStore()
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')

/** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
const scope = openRequestScope(recognition, { notifyNormalErrors: false })
const snapshotRead = createSnapshotRead<ServerMetrics>(scope)
const { status, error, value: snapshot, loaded } = snapshotRead

/** 各段独立降级：服务端读不到的段是 null，卡片显示「不可用」，不整页报错（ADR 0060）。 */
const view = computed(() => (loaded.value ? presentServerMetrics(snapshot.value) : null))

const CARD_TITLES: readonly { key: ServerSectionKey; title: string }[] = [
  { key: 'cpu', title: 'CPU' },
  // 合同里「系统」段只有 jvm.uptimeSeconds（JMX 直读，无 hostname/OS/JDK 字段）。
  { key: 'runtime', title: '系统' },
  { key: 'memory', title: '内存' },
  { key: 'threads', title: '线程' },
  { key: 'disks', title: '磁盘' },
  { key: 'hikari', title: 'Hikari' },
]

const cards = computed(() => {
  const current = view.value
  if (!current) return []
  return CARD_TITLES.map((definition) => ({
    key: definition.key,
    title: definition.title,
    section: current[definition.key],
  }))
})

function load(): Promise<void> {
  return snapshotRead.load(async (requestScope) => {
    const response = await requestScope.request<ServerMetrics | null>(serverMetricsRequest())
    return response.data ?? null
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
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  min-height: 120px;
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
  display: flex;
  flex-direction: column;
  gap: 8px;
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

.disk-row {
  margin-bottom: 12px;

  &:last-child {
    margin-bottom: 0;
  }
}

.disk-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
  font-size: 13px;
}

.disk-mount {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.disk-text {
  color: var(--el-text-color-secondary);
  text-align: right;
}
</style>
