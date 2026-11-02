<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">操作日志</h2>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="操作人">
        <el-input v-model="query.operName" placeholder="操作人" clearable />
      </el-form-item>
      <el-form-item label="模块">
        <el-input v-model="query.title" placeholder="操作模块" clearable />
      </el-form-item>
      <el-form-item label="业务类型">
        <el-select v-model="query.businessType" placeholder="请选业务类型" clearable>
          <el-option
            v-for="item in businessTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="请选状态" clearable>
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间">
        <el-date-picker
          v-model="query.createTimes"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="yyyy-MM-dd HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="action-row">
      <el-button
        v-hasPermi="permissions.remove"
        type="danger"
        plain
        :disabled="!selectedIds.length"
        @click="removeSelected"
        >批量删除</el-button
      >
      <el-button v-hasPermi="permissions.clean" type="danger" @click="cleanAll">清空</el-button>
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <div class="table-scroll">
      <el-table
        v-loading="view.status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        row-key="id"
        stripe
        border
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column
          type="index"
          label="序号"
          width="70"
          align="center"
          :index="list.rowIndex"
        />
        <el-table-column prop="title" label="操作模块" min-width="180" />
        <el-table-column prop="businessTypeLabel" label="业务类型" width="100" />
        <el-table-column prop="operName" label="操作人" width="120" />
        <el-table-column prop="deptName" label="部门" width="120" />
        <el-table-column prop="operIp" label="IP" min-width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.statusTagType" size="small">
              {{ scope.row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTimeText" label="耗时" width="90" />
        <el-table-column prop="createTime" label="操作时间" min-width="170" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button link @click="openDetail(scope.row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ListPagination
      :page="view.page"
      :size="view.size"
      :total="view.total"
      @page-change="list.handlePageChange"
      @size-change="list.handleSizeChange"
    />

    <el-dialog v-model="detailVisible" title="操作日志详情" width="760px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作模块">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ detail.businessTypeLabel }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operName }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ detail.deptName }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求地址">{{ detail.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.operIp }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.statusTagType" size="small">{{ detail.statusLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costTimeText }}</el-descriptions-item>
        <el-descriptions-item label="结果码">{{ detail.resultCodeText }}</el-descriptions-item>
        <el-descriptions-item label="结果信息">{{ detail.resultMessage }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">{{ detail.method }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-block">
        <h4>请求参数</h4>
        <pre class="detail-json">{{ detail.operParamText }}</pre>
      </div>

      <div v-if="detail.errorMsg" class="detail-block">
        <h4>异常信息</h4>
        <pre class="detail-json">{{ detail.errorMsg }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { clean, getById, removeByIds } from '@/api/oper-log'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  BUSINESS_TYPE_OPTIONS,
  OPER_LOG_PERMISSIONS,
  OPER_LOG_STATUS_OPTIONS,
  emptyQuery,
  listRequest,
  presentDetail,
  presentListRecord,
  presentQuery,
} from '@/config/oper-log'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, OperLogDetail, OperLogListItem, OperLogRecord } from '@/types/domain'

export default defineComponent({
  name: 'SysOperLogList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const effects = listPageEffects()
    const list = createListPage<OperLogRecord, OperLogListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...effects,
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: OPER_LOG_PERMISSIONS,
      confirm: effects.confirm,
    }
  },
  data() {
    return {
      query: emptyQuery(),
      businessTypeOptions: BUSINESS_TYPE_OPTIONS,
      statusOptions: OPER_LOG_STATUS_OPTIONS,
      selectedIds: [] as EntityId[],
      detailVisible: false,
      detail: presentDetail() as OperLogDetail,
    }
  },
  methods: {
    search() {
      this.list.search(presentQuery(this.query))
    },
    reset() {
      this.query = emptyQuery()
      this.search()
    },
    onSelectionChange(records: OperLogListItem[]) {
      this.selectedIds = records.map((record) => record.id)
    },
    async removeSelected() {
      if (!this.selectedIds.length) return
      const removed = await this.list.confirmRemove(
        '此操作将永久删除选中的操作日志, 是否继续?',
        () => removeByIds(this.selectedIds),
        '删除成功',
      )
      if (removed) this.selectedIds = []
    },
    async cleanAll() {
      if (!(await this.confirm('清空将删除全部操作日志且不可恢复, 是否继续?'))) return
      const cleaned = await this.list.runWrite(() => clean(), '清空成功')
      if (cleaned) this.selectedIds = []
    },
    async openDetail(id: EntityId) {
      const response = await this.list.settle(getById(id))
      if (!response) return
      this.detail = presentDetail(response.data || {})
      this.detailVisible = true
    },
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

.filter-form {
  margin-bottom: 12px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.detail-block {
  margin-top: 16px;

  h4 {
    margin: 0 0 8px;
    font-size: 14px;
    color: #303133;
  }
}

.detail-json {
  margin: 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 768px) {
  .config-page {
    padding: 16px;
  }

  .filter-form {
    :deep(.el-form-item) {
      display: block;
      width: 100%;
      margin-right: 0;
    }

    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-editor) {
      width: 100%;
    }
  }

  .table-scroll :deep(.el-table) {
    min-width: 1120px;
  }
}
</style>
