<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">审批实例查询</h2>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="审批编号/标题" clearable />
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
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="createTimes"
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

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="processCode" label="审批编号" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="name" label="用户" />
      <el-table-column prop="processTypeName" label="审批类型" />
      <el-table-column prop="processTemplateName" label="审批模板" />
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="90" align="center">
        <template #default="scope">
          <el-button type="text" @click="openDetail(scope.row.detailPath)">查看</el-button>
        </template>
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
  emptyQuery,
  listRequest,
  presentListRecord,
  STATUS_OPTIONS,
} from '@/config/process-instance'
import { createListPage } from '@/list-page'
import { listPageEffects } from '@/list-page-browser'
import { openRequestScope } from '@/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { ProcessInstanceListItem, ProcessInstanceRecord } from '@/types/domain'

export default defineComponent({
  name: 'ProcessInstanceList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<
      ProcessInstanceRecord,
      ProcessInstanceListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...listPageEffects(),
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
    }
  },
  data() {
    return {
      query: emptyQuery(),
      createTimes: [] as string[],
      statusOptions: STATUS_OPTIONS,
    }
  },
  methods: {
    currentQuery(): Record<string, string | number> {
      const query: Record<string, string | number> = { ...this.query }
      if (this.createTimes && this.createTimes.length === 2) {
        query.createTimeBegin = this.createTimes[0]
        query.createTimeEnd = this.createTimes[1]
      } else {
        query.createTimeBegin = ''
        query.createTimeEnd = ''
      }
      return query
    },
    search() {
      this.list.search(this.currentQuery())
    },
    reset() {
      this.query = emptyQuery()
      this.createTimes = []
      this.search()
    },
    openDetail(detailPath: string) {
      this.$router.push(detailPath)
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
