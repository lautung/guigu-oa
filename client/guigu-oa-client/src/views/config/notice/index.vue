<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">通知公告</h2>
      <el-button v-hasPermi="permissions.add" type="primary" @click="openCreate">新建</el-button>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="标题">
        <el-input v-model="query.title" placeholder="公告标题" clearable />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="请选类型" clearable>
          <el-option
            v-for="item in typeOptions"
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
        row-key="id"
        stripe
        border
        class="notice-admin-table"
      >
        <el-table-column
          type="index"
          label="序号"
          width="70"
          align="center"
          :index="list.rowIndex"
        />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            <DictTag
              :dict-type="DICT_TYPES.noticeType"
              :value="scope.row.type"
              :tag-type="scope.row.typeTagType"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <DictTag
              :dict-type="DICT_TYPES.noticeStatus"
              :value="scope.row.status"
              :tag-type="scope.row.statusTagType"
            />
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="scope">
            <el-button
              v-if="canEdit(scope.row)"
              v-hasPermi="permissions.edit"
              link
              @click="openEdit(scope.row)"
              >编辑</el-button
            >
            <el-button
              v-if="canPublish(scope.row)"
              v-hasPermi="permissions.publish"
              link
              @click="publishRow(scope.row)"
              >{{ scope.row.status === 2 ? '再次发布' : '发布' }}</el-button
            >
            <el-button
              v-if="canWithdraw(scope.row)"
              v-hasPermi="permissions.publish"
              link
              @click="withdrawRow(scope.row)"
              >撤回</el-button
            >
            <el-button
              v-hasPermi="permissions.remove"
              link
              type="danger"
              @click="removeRow(scope.row)"
              >删除</el-button
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
import { useRouter } from 'vue-router'

import DictTag from '@/components/DictTag.vue'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { DICT_TYPES } from '@/config/dict'
import { useDict } from '@/dict/use-dict'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { publishNotice, removeNotice, withdrawNotice } from '@/notice/api'
import {
  NOTICE_DELETE_MESSAGE,
  NOTICE_PUBLISH_MESSAGE,
  NOTICE_WITHDRAW_MESSAGE,
} from '@/notice/admin'
import {
  NOTICE_PAGE_SIZE,
  NOTICE_PERMISSIONS,
  adminListRequest,
  emptyAdminQuery,
  presentAdminListRecord,
  presentAdminQuery,
  type NoticeAdminQuery,
} from '@/notice/config'
import { openRequestScope } from '@/request/request-scope'
import { noticeEditPath } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import type { NoticeAdminListItem, NoticeAdminRecord } from '@/types/domain'

/**
 * 系统管理 > 通知公告（#220/ADR 0055）：筛选、分页与写后刷新复用 `createListPage`，
 * 按钮点由 `v-hasPermi` 收口。编辑在隐藏子路由 `/system/notice/edit/:id?`。
 */
export default defineComponent({
  name: 'SysNoticeList',
  components: { DictTag, ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const router = useRouter()
    const effects = listPageEffects()
    const list = createListPage<NoticeAdminRecord, NoticeAdminListItem, NoticeAdminQuery>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => adminListRequest(page, size, presentAdminQuery(query)),
      present: presentAdminListRecord,
      size: NOTICE_PAGE_SIZE,
      ...effects,
    })
    onBeforeUnmount(() => list.release())
    const dict = useDict(DICT_TYPES.noticeType, DICT_TYPES.noticeStatus)
    return {
      list,
      router,
      DICT_TYPES,
      typeOptions: computed(() =>
        dict.optionsOf(DICT_TYPES.noticeType).map((option) => ({
          value: Number(option.dictValue),
          label: option.dictLabel,
        })),
      ),
      statusOptions: computed(() =>
        dict.optionsOf(DICT_TYPES.noticeStatus).map((option) => ({
          value: Number(option.dictValue),
          label: option.dictLabel,
        })),
      ),
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: NOTICE_PERMISSIONS,
      confirm: effects.confirm,
    }
  },
  data() {
    return {
      query: emptyAdminQuery(),
    }
  },
  methods: {
    search() {
      this.list.search(presentAdminQuery(this.query))
    },
    reset() {
      this.query = emptyAdminQuery()
      this.search()
    },
    openCreate() {
      void this.router.push(noticeEditPath())
    },
    openEdit(row: unknown) {
      const record = row as NoticeAdminListItem
      void this.router.push(noticeEditPath(record.id))
    },
    canEdit(row: unknown): boolean {
      const status = (row as NoticeAdminListItem).status
      return status === 0 || status === 1
    },
    canPublish(row: unknown): boolean {
      const status = (row as NoticeAdminListItem).status
      return status === 0 || status === 2
    },
    canWithdraw(row: unknown): boolean {
      return (row as NoticeAdminListItem).status === 1
    },
    async publishRow(row: unknown) {
      const record = row as NoticeAdminListItem
      const message =
        record.status === 2 ? '再次发布后公告将对全员可见，确认发布？' : NOTICE_PUBLISH_MESSAGE
      if (!(await this.confirm(message))) return
      await this.list.runWrite(
        () => publishNotice(record.id),
        record.status === 2 ? '已再次发布' : '发布成功',
      )
    },
    async withdrawRow(row: unknown) {
      const record = row as NoticeAdminListItem
      if (!(await this.confirm(NOTICE_WITHDRAW_MESSAGE))) return
      await this.list.runWrite(() => withdrawNotice(record.id), '已撤回')
    },
    async removeRow(row: unknown) {
      const record = row as NoticeAdminListItem
      await this.list.confirmRemove(
        NOTICE_DELETE_MESSAGE,
        () => removeNotice(record.id),
        '删除成功',
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
    :deep(.el-select) {
      width: 100%;
    }
  }

  .table-scroll :deep(.el-table) {
    min-width: 1080px;
  }
}
</style>
