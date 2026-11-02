<template>
  <div class="b-page">
    <div class="b-page-head">
      <div>
        <h1>审批实例查询</h1>
        <p>共 328 条实例 · 含已结束与已撤回</p>
      </div>
      <div class="b-head-actions">
        <el-button :icon="Download" @click="notImplemented">导出</el-button>
      </div>
    </div>

    <section class="b-filter" :class="{ open: filterOpen }">
      <div v-if="!filterOpen" class="b-filter-bar">
        <el-button size="small" :icon="Filter" @click="filterOpen = true">筛选</el-button>
        <el-tag v-if="query.type" size="small" closable @close="query.type = ''">
          {{ query.type }}
        </el-tag>
        <el-tag v-if="query.status" size="small" closable @close="query.status = ''">
          {{ query.status }}
        </el-tag>
        <el-tag v-if="query.keyword" size="small" closable @close="query.keyword = ''">
          关键词：{{ query.keyword }}
        </el-tag>
        <span v-if="!hasFilter" class="b-filter-hint">未设置筛选条件</span>
        <el-input
          v-model="query.keyword"
          class="b-filter-search"
          size="small"
          placeholder="搜索标题 / 编号 / 发起人"
          :prefix-icon="Search"
          clearable
        />
      </div>
      <el-form v-else class="b-filter-form" :model="query" label-position="top" size="small">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="标题 / 实例编号 / 发起人" clearable />
        </el-form-item>
        <el-form-item label="审批类型">
          <el-select v-model="query.type" placeholder="全部" clearable>
            <el-option v-for="type in types" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
          </el-select>
        </el-form-item>
        <el-form-item label="发起时间">
          <el-date-picker
            v-model="query.range"
            type="daterange"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <div class="b-filter-actions">
          <el-button type="primary" :icon="Search">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button link @click="filterOpen = false">收起</el-button>
        </div>
      </el-form>
    </section>

    <div class="b-actionbar">
      <span class="b-count">共 328 条 · 已选 {{ selected }} 项</span>
      <div class="b-actionbar-right">
        <el-button size="small" :icon="Download" @click="notImplemented">批量导出</el-button>
        <el-button size="small" :icon="Setting" @click="columnDrawer = true">列设置</el-button>
        <el-button size="small" :icon="Refresh" @click="notImplemented">刷新</el-button>
      </div>
    </div>

    <div class="b-table-wrap">
      <el-table
        v-loading="state === 'loading'"
        :data="tableRows"
        size="small"
        class="b-table"
        empty-text="没有符合条件的审批实例"
        @selection-change="(rows) => (selected = rows.length)"
      >
        <el-table-column type="selection" width="38" />
        <el-table-column v-if="visibleColumns.includes('id')" prop="id" label="实例编号" width="146" />
        <el-table-column
          v-if="visibleColumns.includes('title')"
          prop="title"
          label="标题"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column v-if="visibleColumns.includes('type')" prop="type" label="审批类型" width="98" />
        <el-table-column
          v-if="visibleColumns.includes('applicant')"
          prop="applicant"
          label="发起人"
          width="80"
        />
        <el-table-column v-if="visibleColumns.includes('dept')" prop="dept" label="部门" width="86" />
        <el-table-column v-if="visibleColumns.includes('status')" label="状态" width="86">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag[row.status]" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="visibleColumns.includes('node')"
          prop="node"
          label="当前节点"
          width="98"
        />
        <el-table-column
          v-if="visibleColumns.includes('time')"
          prop="time"
          label="发起时间"
          width="106"
        />
        <el-table-column v-if="visibleColumns.includes('cost')" prop="cost" label="耗时" width="76" />
        <el-table-column label="操作" width="96" fixed="right">
          <template #default>
            <el-button link size="small" @click="notImplemented">查看</el-button>
            <el-button link size="small" @click="notImplemented">催办</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="b-pager">
      <span>每页 20 条，共 17 页</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        background
        layout="prev, pager, next"
        :total="328"
      />
    </div>

    <el-drawer v-model="columnDrawer" title="列设置" size="320px">
      <p class="b-drawer-hint">勾选要在表格中显示的列，设置只在本页内存中保留。</p>
      <el-checkbox-group v-model="visibleColumns" class="b-drawer-list">
        <el-checkbox v-for="column in optionalColumns" :key="column.key" :value="column.key">
          {{ column.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="visibleColumns = optionalColumns.map((column) => column.key)">
          恢复默认
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Filter, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { instances, statusTag } from '../../mock'

const props = defineProps({
  state: { type: String, default: 'ready' },
})

const query = reactive({ keyword: '', type: '', status: '', range: null })
const filterOpen = ref(false)
const columnDrawer = ref(false)
const selected = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const types = [...new Set(instances.map((row) => row.type))]
const statuses = ['待审批', '已通过', '已拒绝', '已撤回', '已完成']

const optionalColumns = [
  { key: 'id', label: '实例编号' },
  { key: 'title', label: '标题' },
  { key: 'type', label: '审批类型' },
  { key: 'applicant', label: '发起人' },
  { key: 'dept', label: '部门' },
  { key: 'status', label: '状态' },
  { key: 'node', label: '当前节点' },
  { key: 'time', label: '发起时间' },
  { key: 'cost', label: '耗时' },
]
const visibleColumns = ref(optionalColumns.map((column) => column.key))

const tableRows = computed(() => (props.state === 'empty' ? [] : instances))
const hasFilter = computed(() => !!(query.type || query.status || query.keyword))

function resetQuery() {
  query.keyword = ''
  query.type = ''
  query.status = ''
  query.range = null
}

function notImplemented() {
  ElMessage.info('原型：该动作不实现')
}
</script>

<style scoped>
.b-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1380px;
  min-width: 0;
}

.b-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.b-page-head h1 {
  margin: 0 0 4px;
  font-size: 20px;
}

.b-page-head p {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.b-head-actions {
  display: flex;
  gap: 8px;
}

.b-filter {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 10px 14px;
}

.b-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.b-filter-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.b-filter-search {
  width: 240px;
  margin-left: auto;
}

.b-filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px 16px;
  align-items: end;
}

.b-filter-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.b-filter-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 8px;
  padding-top: 2px;
}

.b-actionbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.b-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.b-actionbar-right {
  display: flex;
  gap: 8px;
}

.b-table-wrap {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  overflow: hidden;
}

.b-table :deep(.el-table__header th) {
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.b-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.b-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.b-drawer-hint {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.b-drawer-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@media (max-width: 1100px) {
  .b-filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .b-filter-search {
    width: 100%;
    margin-left: 0;
  }
}

.is-narrow .b-page-head {
  flex-direction: column;
  align-items: flex-start;
}

.is-narrow .b-filter-form {
  grid-template-columns: minmax(0, 1fr);
}

.is-narrow .b-actionbar {
  flex-direction: column;
  align-items: flex-start;
}

.is-narrow .b-pager {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
</style>
