<template>
  <div class="b-page">
    <div v-if="state === 'loading'" class="b-skeleton-card">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <section class="b-hero" :class="{ empty: state === 'empty' }">
        <div class="b-hero-main">
          <span class="b-eyebrow">今天 · 11 月 2 日 周日</span>
          <div class="b-hero-number">
            {{ state === 'empty' ? '0' : '12' }}
            <small>项待处理</small>
          </div>
          <p v-if="state === 'empty'">待处理已清空，可以发起新的审批。</p>
          <p v-else>其中 3 项今天到期，最早一项已等待 6 小时。</p>
          <div class="b-hero-actions">
            <el-button type="primary" size="large" @click="$emit('go', 'pending')">
              立即处理
            </el-button>
            <el-button size="large" @click="$emit('go', 'start')">发起审批</el-button>
          </div>
        </div>
        <div class="b-hero-side">
          <div class="b-mini">
            <span>今日已办</span>
            <strong>8</strong>
          </div>
          <div class="b-mini">
            <span>我发起的</span>
            <strong>5</strong>
          </div>
          <div class="b-mini">
            <span>抄送我的</span>
            <strong>3</strong>
          </div>
        </div>
      </section>

      <div v-if="state === 'empty'" class="b-empty-card">
        <el-empty description="暂无待处理审批">
          <el-button type="primary" @click="$emit('go', 'start')">发起一条审批</el-button>
        </el-empty>
      </div>

      <div v-else class="b-grid">
        <section class="b-panel">
          <header class="b-panel-head">
            <h3>待我处理</h3>
            <el-button link size="small" @click="$emit('go', 'pending')">查看全部 12 项</el-button>
          </header>
          <div class="b-inbox">
            <article v-for="row in pendingRows" :key="row.id" class="b-inbox-item">
              <span class="b-type-bar" :style="{ background: typeColor[row.type] }"></span>
              <div class="b-inbox-body">
                <div class="b-inbox-title">
                  <strong>{{ row.title }}</strong>
                  <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
                </div>
                <div class="b-inbox-meta">
                  {{ row.applicant }} · {{ row.dept }} · 当前节点 {{ row.node }}
                </div>
              </div>
              <div class="b-inbox-right">
                <span class="b-inbox-time">{{ row.time }}</span>
                <div class="b-inbox-actions">
                  <el-button size="small" type="primary" @click="approve">同意</el-button>
                  <el-button size="small" @click="approve">拒绝</el-button>
                </div>
              </div>
            </article>
          </div>
        </section>

        <aside class="b-rail">
          <section class="b-panel">
            <header class="b-panel-head">
              <h3>公告</h3>
              <el-button link size="small">全部</el-button>
            </header>
            <ul class="b-news">
              <li v-for="item in announcements" :key="item.id">
                <span class="b-news-dot" :class="{ unread: item.unread }"></span>
                <div>
                  <p>{{ item.title }}</p>
                  <small>{{ item.time }}</small>
                </div>
              </li>
            </ul>
          </section>

          <section class="b-panel">
            <header class="b-panel-head">
              <h3>快捷发起</h3>
            </header>
            <div class="b-chips">
              <button v-for="item in quickTypes" :key="item" class="b-chip" @click="$emit('go', 'start')">
                + {{ item }}
              </button>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { announcements, pendingRows, typeColor } from '../../mock'

defineProps({
  state: { type: String, default: 'ready' },
})
defineEmits(['go'])

const quickTypes = ['请假申请', '报销申请', '采购申请', '加班申请']

function approve() {
  ElMessage.info('原型：不提交审批')
}
</script>

<style scoped>
.b-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1280px;
  min-width: 0;
}

.b-skeleton-card,
.b-empty-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  padding: 24px;
}

.b-empty-card {
  padding: 40px 24px;
}

.b-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 26px;
  border-radius: 14px;
  border: 1px solid var(--el-color-primary-light-8);
  background:
    radial-gradient(circle at 82% 18%, var(--el-color-primary-light-8), transparent 46%),
    linear-gradient(120deg, var(--el-color-primary-light-9), var(--el-bg-color) 62%);
}

.b-hero.empty {
  background: var(--el-bg-color);
  border-color: var(--el-border-color-lighter);
}

.b-eyebrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.b-hero-number {
  margin: 6px 0 6px;
  font-size: 52px;
  line-height: 1;
  font-weight: 700;
  color: var(--el-color-primary);
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.b-hero-number small {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.b-hero-main p {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.b-hero-actions {
  display: flex;
  gap: 10px;
}

.b-hero-side {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  min-width: 168px;
}

.b-mini {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 14px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--el-bg-color) 78%, transparent);
  border: 1px solid var(--el-border-color-lighter);
}

.b-mini span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.b-mini strong {
  font-size: 18px;
}

.b-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.b-rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.b-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  overflow: hidden;
}

.b-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.b-panel-head h3 {
  margin: 0;
  font-size: 14px;
}

.b-inbox-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.b-inbox-item:last-child {
  border-bottom: 0;
}

.b-inbox-item:hover {
  background: var(--el-fill-color-lighter);
}

.b-type-bar {
  width: 3px;
  height: 34px;
  border-radius: 3px;
  flex-shrink: 0;
}

.b-inbox-body {
  flex: 1;
  min-width: 0;
}

.b-inbox-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.b-inbox-title strong {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.b-inbox-meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.b-inbox-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.b-inbox-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.b-inbox-actions {
  display: flex;
  gap: 4px;
}

.b-news {
  list-style: none;
  margin: 0;
  padding: 8px 18px 14px;
}

.b-news li {
  display: flex;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.b-news li:last-child {
  border-bottom: 0;
}

.b-news p {
  margin: 0 0 3px;
  font-size: 12px;
  line-height: 1.45;
}

.b-news small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.b-news-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 5px;
  background: var(--el-border-color);
  flex-shrink: 0;
}

.b-news-dot.unread {
  background: var(--el-color-danger);
}

.b-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 18px 18px;
}

.b-chip {
  border: 1px dashed var(--el-border-color);
  background: transparent;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.b-chip:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

@media (max-width: 1100px) {
  .b-hero {
    flex-direction: column;
  }
  .b-hero-side {
    flex-direction: row;
  }
  .b-mini {
    flex: 1;
  }
  .b-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.is-narrow .b-hero {
  flex-direction: column;
  padding: 18px;
}

.is-narrow .b-hero-side {
  flex-direction: row;
  flex-wrap: wrap;
}

.is-narrow .b-grid {
  grid-template-columns: minmax(0, 1fr);
}

.is-narrow .b-inbox-actions {
  display: none;
}
</style>
