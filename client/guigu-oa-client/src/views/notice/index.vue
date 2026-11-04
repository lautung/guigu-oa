<template>
  <div class="notice-page">
    <div class="page-header">
      <h2 class="page-title">全部公告</h2>
      <span class="notice-total">共 {{ view.total }} 条</span>
    </div>

    <el-tabs v-model="tab" class="notice-tabs" @tab-change="changeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="未读" name="unread" />
    </el-tabs>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <div class="table-scroll">
      <el-table
        v-loading="view.status === 'loading'"
        :data="rows"
        :empty-text="emptyText"
        row-key="id"
        stripe
        border
        class="notice-table"
        @row-click="openDetail"
      >
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            <DictTag
              :dict-type="DICT_TYPES.noticeType"
              :value="scope.row.type"
              :tag-type="scope.row.typeTagType"
            />
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="280">
          <template #default="scope">
            <el-button link class="notice-title-link" @click.stop="openDetail(scope.row)">
              {{ scope.row.title }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="140" />
        <el-table-column prop="publishTime" label="发布时间" min-width="170" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.readTagType" size="small">{{ scope.row.readLabel }}</el-tag>
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

import DictTag from '@/components/DictTag.vue'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { DICT_TYPES } from '@/config/dict'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import {
  NOTICE_PAGE_SIZE,
  feedQueryForTab,
  feedRequest,
  presentFeedItem,
  type NoticeFeedQuery,
  type NoticeListTab,
} from '@/notice/config'
import { openRequestScope } from '@/request/request-scope'
import { noticeDetailPath } from '@/router/paths'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, NoticeFeedItem, NoticeFeedRecord } from '@/types/domain'

export default defineComponent({
  name: 'NoticeList',
  components: { DictTag, ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const effects = listPageEffects()
    const list = createListPage<NoticeFeedRecord, NoticeFeedItem, NoticeFeedQuery>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => feedRequest(page, size, query),
      present: presentFeedItem,
      size: NOTICE_PAGE_SIZE,
      query: feedQueryForTab('all'),
      ...effects,
    })
    onBeforeUnmount(() => list.release())
    return { list, DICT_TYPES, view: list.view, rows: list.rows, emptyText: list.emptyText }
  },
  data() {
    return { tab: 'all' as NoticeListTab }
  },
  methods: {
    changeTab(name: string | number) {
      this.tab = name === 'unread' ? 'unread' : 'all'
      this.list.search(feedQueryForTab(this.tab))
    },
    openDetail(row: unknown) {
      const id = (row as { id?: EntityId | '' } | null)?.id
      if (id == null || id === '') return
      void this.$router.push(noticeDetailPath(id))
    },
  },
})
</script>

<style lang="scss" scoped>
.notice-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.notice-total {
  font-size: 13px;
  color: #909399;
}

.notice-tabs {
  margin-bottom: 4px;
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.notice-title-link {
  padding: 0;
  font-size: 14px;
  text-align: left;
  white-space: normal;
}

@media (max-width: 768px) {
  .notice-page {
    padding: 16px;
  }

  .table-scroll :deep(.el-table) {
    min-width: 780px;
  }
}
</style>
