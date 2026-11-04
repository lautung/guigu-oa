<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">在线用户</h2>
      <span class="page-total">共 {{ view.total }} 条</span>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="用户名">
        <el-input v-model="query.username" placeholder="用户名包含" clearable />
      </el-form-item>
      <el-form-item label="登录时间">
        <el-date-picker
          v-model="query.loginTimes"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <div class="table-scroll">
      <el-table
        v-loading="view.status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        row-key="sessionId"
        stripe
        border
      >
        <el-table-column
          type="index"
          label="序号"
          width="70"
          align="center"
          :index="list.rowIndex"
        />
        <el-table-column label="用户名" min-width="180">
          <template #default="scope">
            <span>{{ scope.row.username }}</span>
            <el-tag v-if="scope.row.current" class="current-tag" size="small">当前会话</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="110" />
        <el-table-column prop="loginTime" label="登录时间" min-width="170" />
        <el-table-column prop="ip" label="IP" min-width="130" />
        <el-table-column prop="browser" label="浏览器" width="110" />
        <el-table-column prop="os" label="操作系统" width="110" />
        <el-table-column prop="lastActive" label="最后活跃" min-width="170" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button
              v-hasPermi="permissions.forceLogout"
              link
              type="danger"
              :disabled="scope.row.current"
              @click="forceLogoutRow(scope.row)"
              >强退</el-button
            >
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
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { forceLogout } from '@/api/monitor'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  MONITOR_ONLINE_PERMISSIONS,
  emptyQuery,
  listRequest,
  presentListRecord,
  presentQuery,
} from '@/config/monitor-online'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { OnlineSessionListItem, OnlineSessionRecord } from '@/types/domain'

export default defineComponent({
  name: 'MonitorOnlineList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const effects = listPageEffects()
    const list = createListPage<
      OnlineSessionRecord,
      OnlineSessionListItem,
      Record<string, string | number>
    >({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      // search() 已把表单值投影成查询参数；这里只补 page/size。
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
      permissions: MONITOR_ONLINE_PERMISSIONS,
      confirm: effects.confirm,
    }
  },
  data() {
    return {
      query: emptyQuery(),
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
    async forceLogoutRow(record: {
      sessionId?: string
      username?: string
      loginTime?: string
      ip?: string
      current?: boolean
    }) {
      if (!record.sessionId || record.current) return
      await this.list.confirmRemove(
        `确定强制「${record.username}」的会话下线? 登录时间 ${record.loginTime}，IP ${record.ip}。强退只作废该会话，该用户需要重新登录。`,
        () => forceLogout(record.sessionId as string),
        '已强制下线',
      )
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

.page-total {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.filter-form {
  margin-bottom: 12px;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.current-tag {
  margin-left: 8px;
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
    min-width: 1100px;
  }
}
</style>
