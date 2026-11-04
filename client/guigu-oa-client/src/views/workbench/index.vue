<template>
  <div class="workbench-page">
    <header class="workbench-head">
      <div class="workbench-head-text">
        <h2 class="workbench-title">工作台</h2>
        <p class="workbench-meta">
          <span class="workbench-date">{{ dateLabel }}</span>
          <span class="meta-sep">·</span>
          <span class="pending-headline">{{ headline }}</span>
        </p>
      </div>
      <div class="workbench-actions">
        <el-button @click="go(PATHS.WORK_START)">发起审批</el-button>
        <el-button type="primary" @click="go(PATHS.WORK_PENDING)">处理待办</el-button>
      </div>
    </header>

    <div
      v-if="status === 'loading'"
      class="workbench-card workbench-skeleton"
      :data-rows="WORKBENCH_SKELETON_ROWS"
    >
      <el-skeleton :rows="WORKBENCH_SKELETON_ROWS" animated />
    </div>

    <ListLoadError v-else-if="status === 'failed'" status="failed" :error="error" @retry="load" />

    <template v-else>
      <div class="kpi-grid">
        <button
          v-for="kpi in kpis"
          :key="kpi.key"
          type="button"
          class="kpi-card"
          :class="{ 'is-primary': kpi.emphasis }"
          @click="go(kpi.path)"
        >
          <span class="kpi-label">{{ kpi.label }}</span>
          <strong class="kpi-value">{{ kpi.value }}</strong>
          <span class="kpi-hint">{{ kpi.hint }}<span class="kpi-arrow">→</span></span>
        </button>
      </div>

      <div class="workbench-grid">
        <section class="workbench-card recent-card">
          <header class="card-head">
            <h3 class="card-title">近期审批</h3>
            <el-button link size="small" class="card-link" @click="go(PATHS.WORK_PENDING)">
              查看全部
            </el-button>
          </header>

          <div v-if="recent.length === 0" class="workbench-empty">
            <el-empty description="当前没有待处理事项">
              <el-button type="primary" class="empty-start" @click="go(PATHS.WORK_START)">
                发起审批
              </el-button>
            </el-empty>
          </div>

          <div v-else class="table-scroll">
            <el-table
              :data="recent"
              size="small"
              row-key="rowKey"
              class="recent-table"
              @row-click="openRecent"
            >
              <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
              <el-table-column prop="processTypeName" label="类型" width="104" />
              <el-table-column prop="name" label="发起人" width="90" />
              <el-table-column label="状态" width="92">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.statusTagType">{{ row.statusLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="提交时间" width="160" />
            </el-table>
          </div>
        </section>

        <aside class="workbench-side">
          <section class="workbench-card notice-card">
            <header class="card-head">
              <h3 class="card-title">公告</h3>
              <div class="card-head-actions">
                <span class="unread-count">{{ unreadLabel }}</span>
                <el-button link size="small" class="card-link" @click="go(PATHS.NOTICE)">
                  全部
                </el-button>
              </div>
            </header>

            <ul v-if="notices.length" class="notice-list">
              <li v-for="notice in notices" :key="notice.id">
                <button type="button" class="notice-item" @click="openNotice(notice)">
                  <span class="notice-dot" :class="{ unread: !notice.read }" />
                  <span class="notice-body">
                    <span class="notice-title">{{ notice.title }}</span>
                    <small class="notice-meta"
                      ><DictTag :dict-type="DICT_TYPES.noticeType" :value="notice.type" /> ·
                      {{ notice.publishTime }}</small
                    >
                  </span>
                </button>
              </li>
            </ul>
            <p v-else class="notice-empty">暂无未读公告</p>
          </section>

          <section class="workbench-card quick-card">
            <header class="card-head">
              <h3 class="card-title">快捷入口</h3>
            </header>
            <div class="quick-grid">
              <button
                v-for="link in quickLinks"
                :key="link.key"
                type="button"
                class="quick-link"
                @click="go(link.path)"
              >
                <el-icon class="quick-icon"><component :is="shellIcon(link.icon)" /></el-icon>
                <span>{{ link.title }}</span>
              </button>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import DictTag from '@/components/DictTag.vue'
import ListLoadError from '@/components/ListLoadError.vue'
import { DICT_TYPES } from '@/config/dict'
import { createNoticeUnreadSource } from '@/notice/unread'
import { openRequestScope } from '@/request/request-scope'
import { createSnapshotRead } from '@/request/snapshot-read'
import { PATHS, noticeDetailPath } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import { shellIcon } from '@/shell/shell-icons'
import type {
  NoticeFeedItem,
  PageResult,
  PresentedWorkListRecord,
  ProcessInstanceRecord,
} from '@/types/domain'
import {
  WORKBENCH_SKELETON_ROWS,
  formatWorkbenchDate,
  presentWorkbench,
  workbenchRequests,
  workbenchTotals,
  type WorkbenchView,
} from '@/work/workbench'

defineOptions({ name: 'WorkbenchPage' })

const router = useRouter()
const userStore = useUserStore()
const recognition = userStore.recognition
if (!recognition) throw new Error('认出 module 尚未接入')

/** 一次页面打开一个请求作用域：身份变化后迟到的读取不再画到当前用户页面。 */
const scope = openRequestScope(recognition, { notifyNormalErrors: false })
const requests = workbenchRequests()
/**
 * 公告复用 #219 的读数源（类型/时间/读态投影也在那一处），只做首屏一次读取；
 * 60 秒轮询的唯一实例仍是顶栏铃铛，工作台不另起定时器。
 */
const noticeSource = createNoticeUnreadSource((config) => scope.request(config))

const workbenchRead = createSnapshotRead<WorkbenchView>(scope)
const { status, error, value: data } = workbenchRead
const today = shallowRef(new Date())

const dateLabel = computed(() => formatWorkbenchDate(today.value))
const headline = computed(() => {
  if (status.value === 'loading') return '正在读取待处理…'
  if (status.value === 'failed') return '工作台数据加载失败'
  return data.value?.pendingHeadline || '当前没有待处理事项'
})
const kpis = computed(() => data.value?.kpis || [])
const recent = computed(() => data.value?.recent || [])
const notices = computed(() => data.value?.notices || [])
const quickLinks = computed(() => data.value?.quickLinks || [])
const unreadLabel = computed(() => {
  const count = data.value?.unreadCount || 0
  return count > 0 ? `${count} 条未读` : '暂无未读'
})

function load(): Promise<void> {
  return workbenchRead.load(async (requestScope) => {
    const [pending, processed, started, copied, recentPage, unread, unreadCount] =
      await Promise.all([
        requestScope.request<PageResult<ProcessInstanceRecord>>(requests.total.pending),
        requestScope.request<PageResult<ProcessInstanceRecord>>(requests.total.processed),
        requestScope.request<PageResult<ProcessInstanceRecord>>(requests.total.started),
        requestScope.request<PageResult<ProcessInstanceRecord>>(requests.total.copied),
        requestScope.request<PageResult<ProcessInstanceRecord>>(requests.recent),
        noticeSource.recent(),
        noticeSource.count(),
      ])

    return presentWorkbench({
      totals: workbenchTotals({
        pending: pending.data,
        processed: processed.data,
        started: started.data,
        copied: copied.data,
      }),
      recentPage: recentPage.data,
      notices: unread,
      unreadCount,
    })
  })
}

function go(path: string): void {
  void router.push(path)
}

function openRecent(row: PresentedWorkListRecord): void {
  if (row.detailPath) go(row.detailPath)
}

function openNotice(notice: NoticeFeedItem): void {
  if (notice.id === '' || notice.id == null) return
  go(noticeDetailPath(notice.id))
}

onMounted(() => {
  void load()
})
onBeforeUnmount(() => workbenchRead.release())
</script>

<style lang="scss" scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.workbench-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.workbench-head-text {
  min-width: 0;
}

.workbench-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.workbench-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-sep {
  color: var(--el-border-color);
}

.workbench-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.workbench-card {
  min-width: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
}

.workbench-skeleton {
  padding: 18px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.kpi-card:hover,
.kpi-card:focus-visible {
  border-color: var(--el-color-primary-light-5);
  outline: none;
}

.kpi-card.is-primary {
  border-color: var(--el-color-primary-light-7);
  background: linear-gradient(180deg, var(--el-color-primary-light-9), var(--el-bg-color) 72%);
}

.kpi-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kpi-value {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
}

.kpi-card.is-primary .kpi-value {
  color: var(--el-color-primary);
}

.kpi-hint {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.kpi-arrow {
  color: var(--el-text-color-placeholder);
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.workbench-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.card-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unread-count {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.table-scroll {
  overflow-x: auto;
}

.recent-table {
  min-width: 640px;
}

.workbench-empty {
  padding: 26px 0;
}

.notice-list {
  list-style: none;
  margin: 0;
  padding: 6px 14px 10px;
}

.notice-item {
  display: flex;
  gap: 8px;
  width: 100%;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
}

.notice-list li:last-child .notice-item {
  border-bottom: 0;
}

.notice-item:hover .notice-title,
.notice-item:focus-visible .notice-title {
  color: var(--el-color-primary);
}

.notice-item:focus-visible {
  outline: none;
}

.notice-dot {
  width: 6px;
  height: 6px;
  margin-top: 5px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--el-border-color);
}

.notice-dot.unread {
  background: var(--el-color-danger);
}

.notice-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.notice-title {
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-meta {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.notice-empty {
  margin: 0;
  padding: 20px 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
}

.quick-link:hover,
.quick-link:focus-visible {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  outline: none;
}

.quick-icon {
  flex-shrink: 0;
}

@media (max-width: 1180px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbench-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .kpi-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workbench-head {
    align-items: flex-start;
  }

  .workbench-actions {
    width: 100%;
  }
}
</style>
