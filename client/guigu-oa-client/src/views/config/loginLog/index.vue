<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">登录日志</h2>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="用户名">
        <el-input v-model="query.username" placeholder="用户名" clearable />
      </el-form-item>
      <el-form-item label="IP">
        <el-input v-model="query.ip" placeholder="IP" clearable />
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
      <el-form-item label="登录时间">
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
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="ip" label="IP" min-width="130" />
        <el-table-column prop="browser" label="浏览器" width="110" />
        <el-table-column prop="os" label="操作系统" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <DictTag
              :dict-type="DICT_TYPES.loginStatus"
              :value="scope.row.status"
              :tag-type="scope.row.statusTagType"
            />
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="说明" min-width="160" />
        <el-table-column prop="createTime" label="登录时间" min-width="170" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.username"
              v-hasPermi="permissions.unlock"
              link
              @click="unlockRow(scope.row)"
              >解锁</el-button
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
import { computed, defineComponent, onBeforeUnmount } from 'vue'

import { clean, removeByIds, unlock } from '@/api/login-log'
import DictTag from '@/components/DictTag.vue'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { DICT_TYPES } from '@/config/dict'
import {
  LOGIN_LOG_PERMISSIONS,
  emptyQuery,
  listRequest,
  presentListRecord,
  presentQuery,
} from '@/config/login-log'
import { useDict } from '@/dict/use-dict'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, LoginLogListItem, LoginLogRecord } from '@/types/domain'

export default defineComponent({
  name: 'SysLoginLogList',
  components: { DictTag, ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const effects = listPageEffects()
    const list = createListPage<LoginLogRecord, LoginLogListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...effects,
    })
    onBeforeUnmount(() => list.release())
    const dict = useDict(DICT_TYPES.loginStatus)
    return {
      list,
      DICT_TYPES,
      statusOptions: computed(() =>
        dict.optionsOf(DICT_TYPES.loginStatus).map((option) => ({
          value: Number(option.dictValue),
          label: option.dictLabel,
        })),
      ),
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: LOGIN_LOG_PERMISSIONS,
      confirm: effects.confirm,
    }
  },
  data() {
    return {
      query: emptyQuery(),
      selectedIds: [] as EntityId[],
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
    onSelectionChange(records: LoginLogListItem[]) {
      this.selectedIds = records.map((record) => record.id)
    },
    async removeSelected() {
      if (!this.selectedIds.length) return
      const removed = await this.list.confirmRemove(
        '此操作将永久删除选中的登录日志, 是否继续?',
        () => removeByIds(this.selectedIds),
        '删除成功',
      )
      if (removed) this.selectedIds = []
    },
    async cleanAll() {
      if (!(await this.confirm('清空将删除全部登录日志且不可恢复, 是否继续?'))) return
      const cleaned = await this.list.runWrite(() => clean(), '清空成功')
      if (cleaned) this.selectedIds = []
    },
    async unlockRow(record: { username?: string }) {
      if (!record.username) return
      await this.list.confirmRemove(
        `确定解锁账号「${record.username}」? 解锁后会写入一条解锁日志。`,
        () => unlock(record.username as string),
        '解锁成功',
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
