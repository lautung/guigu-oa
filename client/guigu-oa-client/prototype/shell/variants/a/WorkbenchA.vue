<template>
  <div class="a-page">
    <div class="a-page-head">
      <div>
        <h1>工作台</h1>
        <p>11 月 2 日 周日 · 今天有 3 项审批即将到期</p>
      </div>
      <div class="a-page-actions">
        <el-button @click="$emit('go', 'start')">发起审批</el-button>
        <el-button type="primary" @click="$emit('go', 'pending')">处理待办</el-button>
      </div>
    </div>

    <div v-if="state === 'loading'" class="a-card a-skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else-if="state === 'empty'" class="a-card a-empty">
      <el-empty description="今天没有待处理事项">
        <el-button type="primary" @click="$emit('go', 'start')">发起一条审批</el-button>
      </el-empty>
    </div>

    <template v-else>
      <div class="a-kpis">
        <button class="a-kpi" @click="$emit('go', 'pending')">
          <span class="a-kpi-label">待处理</span>
          <strong class="a-kpi-value">12</strong>
          <span class="a-kpi-foot">3 项今天到期 <el-icon><ArrowRight /></el-icon></span>
        </button>
        <button class="a-kpi" @click="$emit('go', 'done')">
          <span class="a-kpi-label">今日已办</span>
          <strong class="a-kpi-value">8</strong>
          <span class="a-kpi-foot">平均处理 1.4 小时</span>
        </button>
        <button class="a-kpi" @click="$emit('go', 'mine')">
          <span class="a-kpi-label">我发起的</span>
          <strong class="a-kpi-value">5</strong>
          <span class="a-kpi-foot">2 条进行中</span>
        </button>
        <button class="a-kpi" @click="$emit('go', 'cc')">
          <span class="a-kpi-label">抄送我的</span>
          <strong class="a-kpi-value">3</strong>
          <span class="a-kpi-foot">本周新增</span>
        </button>
      </div>

      <div class="a-wb-grid">
        <section class="a-card">
          <header class="a-card-head">
            <h3>近期审批</h3>
            <el-button link size="small" @click="$emit('go', 'pending')">查看全部</el-button>
          </header>
          <el-table :data="pendingRows" size="small" class="a-recent">
            <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="92" />
            <el-table-column prop="applicant" label="发起人" width="76" />
            <el-table-column label="状态" width="82">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag[row.status]">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="提交时间" width="100" />
          </el-table>
        </section>

        <div class="a-wb-side">
          <section class="a-card">
            <header class="a-card-head">
              <h3>公告</h3>
              <el-button link size="small">全部</el-button>
            </header>
            <ul class="a-notices">
              <li v-for="item in announcements" :key="item.id">
                <span class="a-dot" :class="{ unread: item.unread }"></span>
                <div>
                  <p>{{ item.title }}</p>
                  <small>{{ item.type }} · {{ item.time }}</small>
                </div>
              </li>
            </ul>
          </section>

          <section class="a-card">
            <header class="a-card-head">
              <h3>快捷入口</h3>
            </header>
            <div class="a-quick">
              <button v-for="item in quickLinks" :key="item.key" @click="$emit('go', item.key)">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </button>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { announcements, pendingRows, statusTag } from '../../mock'

defineProps({
  state: { type: String, default: 'ready' },
})
defineEmits(['go'])

const quickLinks = [
  { key: 'start', title: '发起审批', icon: 'EditPen' },
  { key: 'pending', title: '待处理', icon: 'AlarmClock' },
  { key: 'mine', title: '已发起', icon: 'Promotion' },
  { key: 'instanceList', title: '实例查询', icon: 'Files' },
]
</script>

<style scoped>
.a-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.a-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.a-page-head h1 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
}

.a-page-head p {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.a-page-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.a-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.a-kpi {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease;
}

.a-kpi:hover {
  border-color: var(--el-color-primary-light-5);
}

.a-kpi:first-child {
  border-color: var(--el-color-primary-light-7);
  background: linear-gradient(
    180deg,
    var(--el-color-primary-light-9),
    var(--el-bg-color) 72%
  );
}

.a-kpi-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.a-kpi-value {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
}

.a-kpi:first-child .a-kpi-value {
  color: var(--el-color-primary);
}

.a-kpi-foot {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.a-wb-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.a-wb-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.a-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
}

.a-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.a-card-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.a-recent {
  padding: 8px 12px 10px;
}

.a-notices {
  list-style: none;
  margin: 0;
  padding: 6px 14px 10px;
}

.a-notices li {
  display: flex;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.a-notices li:last-child {
  border-bottom: 0;
}

.a-notices p {
  margin: 0 0 3px;
  font-size: 12px;
  line-height: 1.45;
}

.a-notices small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.a-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 5px;
  background: var(--el-border-color);
  flex-shrink: 0;
}

.a-dot.unread {
  background: var(--el-color-danger);
}

.a-quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
}

.a-quick button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
}

.a-quick button:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.a-skeleton {
  padding: 18px;
}

.a-empty {
  padding: 40px 0;
}

@media (max-width: 1180px) {
  .a-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .a-wb-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.is-narrow .a-page-head {
  flex-direction: column;
  align-items: flex-start;
}

.is-narrow .a-kpis {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.is-narrow .a-wb-grid {
  grid-template-columns: minmax(0, 1fr);
}
</style>
