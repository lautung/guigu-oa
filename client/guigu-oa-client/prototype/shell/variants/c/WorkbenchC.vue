<template>
  <div class="c-page">
    <div class="c-page-head">
      <div>
        <h1>工作台</h1>
        <p>11 月 2 日 周日 · 今天 3 项到期</p>
      </div>
      <div class="c-statline">
        <span><b>12</b> 待处理</span>
        <span><b>8</b> 今日已办</span>
        <span><b>5</b> 我发起</span>
        <span><b>3</b> 抄送</span>
      </div>
    </div>

    <div v-if="state === 'loading'" class="c-panel c-skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="state === 'empty'" class="c-panel c-empty">
      <el-empty description="今天没有待处理事项">
        <el-button type="primary" @click="$emit('go', 'start')">发起一条审批</el-button>
      </el-empty>
    </div>

    <div v-else class="c-grid">
      <div class="c-col">
        <section class="c-panel">
          <header class="c-panel-head">
            <span>待处理 <em>12</em></span>
            <el-button link size="small" @click="$emit('go', 'pending')">全部</el-button>
          </header>
          <div class="c-rows">
            <article v-for="row in pendingRows" :key="row.id" class="c-row">
              <span class="c-dot" :style="{ background: typeColor[row.type] }"></span>
              <div class="c-row-main">
                <strong>{{ row.title }}</strong>
                <small>{{ row.applicant }} · {{ row.dept }} · {{ row.time }}</small>
              </div>
              <span class="c-node">{{ row.node }}</span>
              <div class="c-row-actions">
                <el-button link size="small" type="primary" @click="approve">同意</el-button>
                <el-button link size="small" @click="approve">拒绝</el-button>
              </div>
            </article>
          </div>
        </section>

        <section class="c-panel">
          <header class="c-panel-head">
            <span>我的申请</span>
            <el-button link size="small" @click="$emit('go', 'mine')">全部</el-button>
          </header>
          <div class="c-rows">
            <article v-for="row in myRows" :key="row.id" class="c-row">
              <span class="c-dot" :class="`is-${statusKey(row.status)}`"></span>
              <div class="c-row-main">
                <strong>{{ row.title }}</strong>
                <small>{{ row.id }} · {{ row.time }}</small>
              </div>
              <span class="c-status">{{ row.status }}</span>
            </article>
          </div>
        </section>
      </div>

      <div class="c-col">
        <section class="c-panel">
          <header class="c-panel-head">
            <span>公告</span>
            <el-button link size="small">全部</el-button>
          </header>
          <ul class="c-news">
            <li v-for="item in announcements" :key="item.id">
              <span class="c-news-dot" :class="{ unread: item.unread }"></span>
              <div>
                <p>{{ item.title }}</p>
                <small>{{ item.type }} · {{ item.time }}</small>
              </div>
            </li>
          </ul>
        </section>

        <section class="c-panel">
          <header class="c-panel-head">
            <span>快捷入口</span>
          </header>
          <div class="c-quick">
            <button v-for="item in quickLinks" :key="item.key" @click="$emit('go', item.key)">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { announcements, myRows, pendingRows, typeColor } from '../../mock'

defineProps({
  state: { type: String, default: 'ready' },
})
defineEmits(['go'])

const quickLinks = [
  { key: 'start', title: '发起审批', icon: 'EditPen' },
  { key: 'pending', title: '待处理', icon: 'AlarmClock' },
  { key: 'instanceList', title: '实例查询', icon: 'Files' },
  { key: 'notice', title: '公告', icon: 'Bell' },
]

const statusKey = (status) =>
  ({ 已完成: 'done', 已通过: 'ok', 已拒绝: 'reject', 已撤回: 'withdraw' })[status] || 'wait'

function approve() {
  ElMessage.info('原型：不提交审批')
}
</script>

<style scoped>
.c-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 1440px;
  min-width: 0;
}

.c-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.c-page-head h1 {
  margin: 0 0 2px;
  font-size: 18px;
  font-weight: 700;
}

.c-page-head p {
  margin: 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.c-statline {
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.c-statline b {
  font-size: 15px;
  color: var(--el-text-color-primary);
  margin-right: 2px;
}

.c-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.c-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.c-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.c-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  font-weight: 600;
}

.c-panel-head em {
  font-style: normal;
  font-weight: 500;
  font-size: 10px;
  margin-left: 4px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.c-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.c-row:last-child {
  border-bottom: 0;
}

.c-row:hover {
  background: var(--el-fill-color-lighter);
}

.c-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.c-dot.is-done,
.c-dot.is-ok {
  background: var(--el-color-success);
}

.c-dot.is-reject {
  background: var(--el-color-danger);
}

.c-dot.is-withdraw {
  background: var(--el-text-color-placeholder);
}

.c-dot.is-wait {
  background: var(--el-color-warning);
}

.c-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.c-row-main strong {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.c-row-main small {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.c-node,
.c-status {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  padding: 1px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.c-row-actions {
  flex-shrink: 0;
  display: flex;
  gap: 2px;
  opacity: 0;
}

.c-row:hover .c-row-actions {
  opacity: 1;
}

.c-news {
  list-style: none;
  margin: 0;
  padding: 4px 12px 8px;
}

.c-news li {
  display: flex;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.c-news li:last-child {
  border-bottom: 0;
}

.c-news p {
  margin: 0 0 2px;
  font-size: 12px;
  line-height: 1.4;
}

.c-news small {
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.c-news-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-top: 5px;
  background: var(--el-border-color);
  flex-shrink: 0;
}

.c-news-dot.unread {
  background: var(--el-color-danger);
}

.c-quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 10px 12px 12px;
}

.c-quick button {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 7px 9px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.c-quick button:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.c-skeleton {
  padding: 16px;
}

.c-empty {
  padding: 30px 0;
}

@media (max-width: 1100px) {
  .c-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .c-statline {
    display: none;
  }
}

.is-narrow .c-grid {
  grid-template-columns: minmax(0, 1fr);
}

.is-narrow .c-statline {
  display: none;
}

.is-narrow .c-row-main {
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.is-narrow .c-row-actions {
  opacity: 1;
}

.is-narrow .c-row-main small {
  max-width: 100%;
}
</style>
