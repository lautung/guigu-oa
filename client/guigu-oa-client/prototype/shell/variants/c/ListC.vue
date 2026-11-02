<template>
  <div class="c-page">
    <div class="c-page-head">
      <div>
        <h1>审批实例查询</h1>
        <p>共 328 条 · 含已结束与已撤回</p>
      </div>
    </div>

    <div class="c-toolbar">
      <div class="c-seg">
        <button
          v-for="type in typeFilters"
          :key="type"
          :class="{ active: query.type === type }"
          @click="query.type = type"
        >
          {{ type || '全部' }}
        </button>
      </div>
      <el-select v-model="query.status" size="small" placeholder="状态" clearable class="c-status-select">
        <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
      </el-select>
      <div class="c-dates">
        <button
          v-for="range in ranges"
          :key="range.key"
          :class="{ active: query.range === range.key }"
          @click="query.range = range.key"
        >
          {{ range.label }}
        </button>
      </div>
      <div class="c-toolbar-right">
        <el-input
          v-model="query.keyword"
          size="small"
          placeholder="搜索标题 / 编号"
          :prefix-icon="Search"
          clearable
          class="c-search-input"
        />
        <el-popover trigger="click" placement="bottom-end" :width="180">
          <template #reference>
            <button class="c-tool-btn" title="列设置">
              <el-icon><Setting /></el-icon>
            </button>
          </template>
          <el-checkbox-group v-model="visibleColumns" class="c-colset">
            <el-checkbox v-for="column in optionalColumns" :key="column.key" :value="column.key">
              {{ column.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-popover>
        <button class="c-tool-btn" title="导出" @click="notImplemented">
          <el-icon><Download /></el-icon>
        </button>
      </div>
    </div>

    <el-table
      v-loading="state === 'loading'"
      :data="tableRows"
      size="small"
      class="c-table"
      empty-text="没有符合条件的审批实例"
      @selection-change="(rows) => (selected = rows.length)"
    >
      <el-table-column type="selection" width="34" />
      <el-table-column v-if="visibleColumns.includes('id')" prop="id" label="实例编号" width="146" />
      <el-table-column
        v-if="visibleColumns.includes('title')"
        prop="title"
        label="标题"
        min-width="190"
        show-overflow-tooltip
      />
      <el-table-column v-if="visibleColumns.includes('type')" prop="type" label="类型" width="92" />
      <el-table-column
        v-if="visibleColumns.includes('applicant')"
        prop="applicant"
        label="发起人"
        width="76"
      />
      <el-table-column v-if="visibleColumns.includes('dept')" prop="dept" label="部门" width="82" />
      <el-table-column v-if="visibleColumns.includes('status')" label="状态" width="88">
        <template #default="{ row }">
          <span class="c-status">
            <span class="c-status-dot" :class="`is-${statusKey(row.status)}`"></span>
            {{ row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('node')"
        prop="node"
        label="当前节点"
        width="94"
      />
      <el-table-column
        v-if="visibleColumns.includes('time')"
        prop="time"
        label="发起时间"
        width="102"
      />
      <el-table-column v-if="visibleColumns.includes('cost')" prop="cost" label="耗时" width="72" />
      <el-table-column label="操作" width="92" fixed="right">
        <template #default>
          <el-button link size="small" @click="notImplemented">查看</el-button>
          <el-button link size="small" @click="notImplemented">催办</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="c-pager">
      <span>已选 {{ selected }} 项 · 1–20 / 328</span>
      <div class="c-pager-right">
        <el-select size="small" model-value="20" class="c-size">
          <el-option label="10 / 页" value="10" />
          <el-option label="20 / 页" value="20" />
          <el-option label="50 / 页" value="50" />
        </el-select>
        <button class="c-page-btn" disabled>上一页</button>
        <button class="c-page-btn is-current">1</button>
        <button class="c-page-btn">2</button>
        <button class="c-page-btn">3</button>
        <button class="c-page-btn">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Search, Setting } from '@element-plus/icons-vue'
import { instances } from '../../mock'

const props = defineProps({
  state: { type: String, default: 'ready' },
})

const query = reactive({ keyword: '', type: '', status: '', range: '' })
const selected = ref(0)
const typeFilters = ['', '请假申请', '报销申请', '采购申请', '加班申请']
const statuses = ['待审批', '已通过', '已拒绝', '已撤回', '已完成']
const ranges = [
  { key: 'today', label: '今天' },
  { key: 'week', label: '近 7 天' },
  { key: 'month', label: '近 30 天' },
]

const optionalColumns = [
  { key: 'id', label: '实例编号' },
  { key: 'title', label: '标题' },
  { key: 'type', label: '类型' },
  { key: 'applicant', label: '发起人' },
  { key: 'dept', label: '部门' },
  { key: 'status', label: '状态' },
  { key: 'node', label: '当前节点' },
  { key: 'time', label: '发起时间' },
  { key: 'cost', label: '耗时' },
]
const visibleColumns = ref(optionalColumns.map((column) => column.key))

const tableRows = computed(() => (props.state === 'empty' ? [] : instances))

const statusKey = (status) =>
  ({ 待审批: 'wait', 已通过: 'ok', 已拒绝: 'reject', 已撤回: 'withdraw', 已完成: 'done' })[status] ||
  'wait'

function notImplemented() {
  ElMessage.info('原型：该动作不实现')
}
</script>

<style scoped>
.c-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 1440px;
  min-width: 0;
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

.c-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.c-seg,
.c-dates {
  display: flex;
  gap: 2px;
  padding: 2px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.c-seg button,
.c-dates button {
  border: 0;
  background: transparent;
  border-radius: 4px;
  padding: 4px 9px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
}

.c-seg button:hover,
.c-dates button:hover {
  color: var(--el-text-color-primary);
}

.c-seg button.active,
.c-dates button.active {
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.c-status-select {
  width: 104px;
}

.c-toolbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.c-search-input {
  width: 190px;
}

.c-tool-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.c-tool-btn:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.c-table {
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.c-table :deep(.el-table__header th) {
  background: var(--el-fill-color-lighter);
  font-weight: 600;
  color: var(--el-text-color-regular);
  height: 34px;
}

.c-table :deep(.el-table__row td) {
  padding: 5px 0;
}

.c-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.c-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
}

.c-status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.c-status-dot.is-wait {
  background: var(--el-color-warning);
}

.c-status-dot.is-ok,
.c-status-dot.is-done {
  background: var(--el-color-success);
}

.c-status-dot.is-reject {
  background: var(--el-color-danger);
}

.c-status-dot.is-withdraw {
  background: var(--el-text-color-placeholder);
}

.c-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.c-pager-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.c-size {
  width: 92px;
}

.c-page-btn {
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  border-radius: 4px;
  height: 24px;
  min-width: 24px;
  padding: 0 7px;
  font-size: 11px;
  cursor: pointer;
}

.c-page-btn:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.c-page-btn.is-current {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}

.c-page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.c-colset {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

@media (max-width: 1100px) {
  .c-toolbar-right {
    margin-left: 0;
    width: 100%;
  }
  .c-search-input {
    flex: 1;
  }
}

.is-narrow .c-toolbar {
  align-items: flex-start;
}

.is-narrow .c-toolbar-right {
  margin-left: 0;
  width: 100%;
}

.is-narrow .c-search-input {
  flex: 1;
  width: auto;
}

.is-narrow .c-pager {
  flex-direction: column;
  align-items: flex-start;
}
</style>
