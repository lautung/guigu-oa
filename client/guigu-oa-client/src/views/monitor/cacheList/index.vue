<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">缓存列表</h2>
      <el-button
        v-hasPermi="permissions.clean"
        class="clear-cache-button"
        type="danger"
        plain
        @click="clearAll"
        >清理权限缓存</el-button
      >
    </div>

    <div class="filter-bar">
      <span class="filter-label">键前缀</span>
      <!-- 受控绑定：change 时 prefix 还是旧值，切换判定与重置都在 switchPrefix 里做。 -->
      <el-select :model-value="prefix" class="prefix-select" @change="switchPrefix">
        <el-option v-for="option in prefixOptions" :key="option" :label="option" :value="option" />
      </el-select>
    </div>

    <ListLoadError :status="status" :error="error" @retry="refresh" />

    <div class="table-scroll">
      <el-table
        v-loading="status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        row-key="key"
        stripe
        border
      >
        <el-table-column prop="key" label="键名" min-width="320" show-overflow-tooltip />
        <el-table-column prop="ttlLabel" label="TTL" width="140" align="right" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.deletable"
              v-hasPermi="permissions.clean"
              link
              type="danger"
              @click="removeRow(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="hasMore" class="load-more-bar">
      <el-button class="load-more-button" :loading="status === 'loading'" @click="loadMore"
        >加载更多</el-button
      >
    </div>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, shallowRef } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import { deleteCacheKey, clearPermissionCache } from '@/api/monitor'
import {
  CACHE_PREFIX_OPTIONS,
  DEFAULT_KEY_COUNT,
  MONITOR_CACHE_PERMISSIONS,
  cacheKeysRequest,
  presentCacheKeyRow,
  refreshCountOf,
  type CacheKeyRow,
  type CachePrefix,
} from '@/config/monitor-cache'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { createSnapshotRead } from '@/request/snapshot-read'
import { useUserStore } from '@/store/modules/user'
import type { CacheKeyPage } from '@/types/domain'

export default defineComponent({
  name: 'MonitorCacheListPage',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')

    /** 一次页面打开一个请求作用域（工作台同款）：身份变化后迟到的读取不再画到当前页面。 */
    const scope = openRequestScope(recognition, { notifyNormalErrors: false })
    const effects = listPageEffects()

    const prefix = shallowRef<CachePrefix>('perm:')
    /** 下一窗口的 SCAN 游标；0 表示扫描结束（服务端口径）。 */
    const cursor = shallowRef(0)
    const cacheRead = createSnapshotRead<CacheKeyRow[]>(scope)
    const { status, error, loaded } = cacheRead
    const rows = computed(() => cacheRead.value.value ?? [])

    const hasMore = computed(() => loaded.value && cursor.value !== 0)
    const emptyText = computed(() => (loaded.value ? '暂无数据' : ''))

    /**
     * 两种读法共用 sequence 丢迟到：refresh 是「写后刷新当前页」——把已累计的
     * 窗口整段重读（cursor 归零），more 继续当前游标向后追加一窗。
     */
    function runLoad(kind: 'refresh' | 'more'): Promise<void> {
      return cacheRead.load(async (requestScope, isCurrent) => {
        const cursorValue = kind === 'more' ? cursor.value : 0
        const count = kind === 'more' ? DEFAULT_KEY_COUNT : refreshCountOf(rows.value.length)
        const response = await requestScope.request<CacheKeyPage>(
          cacheKeysRequest({ prefix: prefix.value, cursor: cursorValue, count }),
        )
        if (!isCurrent()) return rows.value
        const page = response.data ?? { keys: [], nextCursor: 0 }
        const windowRows = (page.keys ?? []).map(presentCacheKeyRow)
        cursor.value =
          typeof page.nextCursor === 'number' && page.nextCursor > 0 ? page.nextCursor : 0
        return kind === 'more' ? [...rows.value, ...windowRows] : windowRows
      })
    }

    /** 写后刷新当前页：整段重读已累计的窗口；窗口为空时退回一个默认首页。 */
    function refresh(): void {
      void runLoad('refresh')
    }

    function loadMore(): void {
      void runLoad('more')
    }

    /** 前缀切换：重置游标与累计行，重新读第一个窗口。 */
    function switchPrefix(value: string): void {
      if (!(CACHE_PREFIX_OPTIONS as readonly string[]).includes(value)) return
      if (value === prefix.value) return
      prefix.value = value as CachePrefix
      cacheRead.reset([])
      cursor.value = 0
      refresh()
    }

    /** 行内删除：只收 perm: 前缀（ADR 0060），二次确认后删除并刷新当前页。 */
    async function removeRow(row: { key?: string | null; deletable?: boolean }): Promise<void> {
      if (!row?.deletable || !row.key) return
      const confirmed = await effects.confirm(
        `确定删除缓存键「${row.key}」? 该键属于权限缓存（perm:*），删除后相关用户的权限将在下一次访问时重建。`,
      )
      if (!confirmed) return
      try {
        await deleteCacheKey(row.key)
      } catch {
        // 请求层已上屏失败提示；失败不刷新。
        return
      }
      effects.notify('已删除缓存键', 'success')
      refresh()
    }

    /** 清空全部权限缓存键（perm:cache:*）：二次确认，成功后报删除数量并刷新当前页。 */
    async function clearAll(): Promise<void> {
      const confirmed = await effects.confirm(
        '确定清空全部权限缓存键? 将删除所有 perm:cache:* 键，相关用户的权限将在下一次访问时重建。',
      )
      if (!confirmed) return
      let countText = '已清理权限缓存键'
      try {
        const response = await clearPermissionCache()
        if (typeof response.data === 'number') {
          countText = `已清理 ${response.data} 个权限缓存键`
        }
      } catch {
        // 请求层已上屏失败提示；失败不刷新。
        return
      }
      effects.notify(countText, 'success')
      refresh()
    }

    onMounted(() => {
      refresh()
    })
    onBeforeUnmount(() => cacheRead.release())

    return {
      prefixOptions: CACHE_PREFIX_OPTIONS,
      permissions: MONITOR_CACHE_PERMISSIONS,
      prefix,
      rows,
      status,
      error,
      emptyText,
      hasMore,
      refresh,
      loadMore,
      switchPrefix,
      removeRow,
      clearAll,
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.prefix-select {
  width: 220px;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.load-more-bar {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .config-page {
    padding: 16px;
  }

  .prefix-select {
    width: 100%;
  }

  .table-scroll :deep(.el-table) {
    min-width: 560px;
  }
}
</style>
