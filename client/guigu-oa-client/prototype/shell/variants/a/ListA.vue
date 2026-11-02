<template>
  <div class="a-page">
    <div class="a-page-head">
      <div>
        <h1>审批实例查询</h1>
        <p>共 328 条实例 · 含已结束</p>
      </div>
      <el-button :icon="Download" @click="notImplemented">导出</el-button>
    </div>

    <el-form class="a-query" :inline="true" :model="query" size="small" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="标题 / 实例编号 / 发起人"
          clearable
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="审批类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 122px">
          <el-option v-for="type in types" :key="type" :label="type" :value="type" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
          <el-option v-for="status in statuses" :key="status" :label="status" :value="status" />
        </el-select>
      </el-form-item>
      <el-form-item label="发起时间">
        <el-date-picker
          v-model="query.range"
          type="daterange"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 224px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="a-actionbar">
      <div class="a-actionbar-left">
        <span class="a-count">共 328 条</span>
        <el-tag v-if="query.type" size="small" closable @close="query.type = ''">
          {{ query.type }}
        </el-tag>
        <el-tag v-if="query.status" size="small" closable @close="query.status = ''">
          {{ query.status }}
        </el-tag>
      </div>
      <div class="a-actionbar-right">
        <el-button size="small" :icon="Download" @click="notImplemented">导出</el-button>
        <el-popover trigger="click" placement="bottom-end" :width="180">
          <template #reference>
            <el-button size="small" :icon="Setting">列设置</el-button>
          </template>
          <el-checkbox-group v-model="visibleColumns" class="a-colset">
            <el-checkbox v-for="column in optionalColumns" :key="column.key" :value="column.key">
              {{ column.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-popover>
      </div>
    </div>

    <el-table
      v-loading="state === 'loading'"
      :data="tableRows"
      size="small"
      border
      stripe
      class="a-table"
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
          <el-tag size="small" :type="statusTag[row.status]">{{ row.status }}</el-tag>
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

    <div class="a-pager">
      <span class="a-selected">已选 {{ selected }} 项</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="328"
        :page-sizes="[10, 20, 50]"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { instances, statusTag } from '../../mock'

const props = defineProps({
  state: { type: String, default: 'ready' },
})

const query = reactive({ keyword: '', type: '', status: '', range: null })
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
.a-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.a-query {
  padding: 10px 12px 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.a-query :deep(.el-form-item) {
  margin-bottom: 10px;
}

.a-actionbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.a-actionbar-left,
.a-actionbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.a-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.a-table {
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.a-colset {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.a-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
}

.a-selected {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .a-page-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .a-query :deep(.el-form-item) {
    display: flex;
    width: 100%;
  }
  .a-query :deep(.el-form-item__content) {
    flex: 1;
  }
  .a-pager {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
