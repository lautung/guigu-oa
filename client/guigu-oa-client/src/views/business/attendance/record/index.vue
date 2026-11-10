<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">打卡记录</h2>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="姓名">
        <el-input v-model="name" placeholder="姓名或用户名" clearable />
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="source" placeholder="全部来源" clearable style="width: 130px">
          <el-option
            v-for="option in sourceOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
      <el-table-column prop="punchDate" label="打卡日期" width="120" align="center" />
      <el-table-column label="卡类型" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.slot === 1 ? 'success' : 'info'" size="small">
            {{ scope.row.slotLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="punchTime" label="打卡时间" width="170" />
      <el-table-column label="来源" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.source === 2 ? 'warning' : 'info'" size="small">
            {{ scope.row.sourceLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="补卡人" width="110" align="center">
        <template #default="scope">{{ scope.row.operatorName || '—' }}</template>
      </el-table-column>
    </el-table>

    <ListPagination
      :page="view.page"
      :size="view.size"
      :total="view.total"
      @page-change="list.handlePageChange"
      @size-change="list.handleSizeChange"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  PUNCH_SOURCE_OPTIONS,
  presentPunchRecord,
  punchListRequest,
  punchQueryOf,
} from '@/config/attendance'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { AttendancePunchListItem, AttendancePunchRecord } from '@/types/domain'

/**
 * 后台打卡记录（#279，ADR 0073）：读只判页面 `/business/attendance/record` 授予；
 * 数据范围由服务端按 sys_user.dept_id（当前）与 sys_user.id 套五档，客户端不做行级过滤。
 * 列表行 join 姓名/部门/补卡操作人；筛选按人、日期区间与来源。
 */
export default defineComponent({
  name: 'AttendanceRecordPage',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<
      AttendancePunchRecord,
      AttendancePunchListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => punchListRequest(page, size, query),
      present: presentPunchRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      sourceOptions: PUNCH_SOURCE_OPTIONS,
    }
  },
  data() {
    return {
      name: '',
      dateRange: [] as string[],
      source: '' as number | '',
    }
  },
  methods: {
    search() {
      this.list.search(
        punchQueryOf({
          name: this.name,
          beginDate: this.dateRange?.[0],
          endDate: this.dateRange?.[1],
          source: this.source,
        }),
      )
    },
    reset() {
      this.name = ''
      this.dateRange = []
      this.source = ''
      this.search()
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
</style>
