<template>
  <div class="config-page monitor-page">
    <div class="page-header">
      <h2 class="page-title">缓存监控</h2>
      <el-button class="refresh-button" :loading="status === 'loading'" @click="load"
        >刷新</el-button
      >
    </div>

    <ListLoadError v-if="status === 'failed'" status="failed" :error="error" @retry="load" />

    <div v-if="view" v-loading="status === 'loading' && !loaded" class="metric-grid">
      <section v-for="card in cards" :key="card.key" class="metric-card">
        <h3 class="metric-title">{{ card.title }}</h3>
        <template v-if="card.key === 'keyspace'">
          <el-table
            v-if="view.keyspaceAvailable"
            :data="view.dbs"
            :empty-text="'暂无数据'"
            stripe
            border
          >
            <el-table-column prop="db" label="数据库" min-width="90" />
            <el-table-column prop="keys" label="键数" width="100" align="right" />
            <el-table-column prop="expires" label="带过期" width="100" align="right" />
            <el-table-column prop="avgTtl" label="平均 TTL" width="120" align="right" />
          </el-table>
          <p v-else class="metric-unavailable">不可用</p>
        </template>
        <template v-else-if="card.section.available">
          <dl class="metric-lines">
            <div v-for="line in card.section.lines" :key="line.label" class="metric-line">
              <dt>{{ line.label }}</dt>
              <dd>{{ line.value }}</dd>
            </div>
          </dl>
        </template>
        <p v-else class="metric-unavailable">不可用</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import { cacheInfoRequest, presentCacheInfo } from '@/config/monitor-cache'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { CacheMonitorSnapshot } from '@/types/domain'

defineOptions({ name: 'MonitorCachePage' })

const userStore = useUserStore()
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')

/** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
const scope = openRequestScope(recognition, { notifyNormalErrors: false })

const status = shallowRef<'loading' | 'ready' | 'failed'>('loading')
const error = shallowRef('')
const snapshot = shallowRef<CacheMonitorSnapshot | null>(null)
/** 已拿到过一次响应：之后的刷新失败保留旧卡片，只叠加错误行。 */
const loaded = shallowRef(false)
let sequence = 0

/** 各段独立降级：服务端读不到的段是 null，卡片显示「不可用」，不整页报错（ADR 0060）。 */
const view = computed(() => (loaded.value ? presentCacheInfo(snapshot.value) : null))

const CARD_TITLES: readonly { key: string; title: string }[] = [
  { key: 'server', title: 'Redis 信息' },
  { key: 'memory', title: '内存' },
  { key: 'clients', title: '客户端与命中率' },
  { key: 'keyspace', title: '各 db 键数' },
]

const cards = computed(() => {
  const current = view.value
  if (!current) return []
  return CARD_TITLES.map((definition) => ({
    key: definition.key,
    title: definition.title,
    section:
      definition.key === 'server'
        ? current.server
        : definition.key === 'memory'
          ? current.memory
          : current.clients,
  }))
})

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
    const response = await scope.request<CacheMonitorSnapshot | null>(cacheInfoRequest())
    if (number !== sequence || !scope.current()) return
    snapshot.value = response.data ?? null
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
</style>
