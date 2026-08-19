<template>
  <div class="pending-page">
    <h2 class="page-title">待处理</h2>

    <div v-loading="loading && list.length === 0" class="list-container">
      <el-empty v-if="!loading && list.length === 0" description="暂无待处理审批" />

      <div
        v-for="item in list"
        :key="`${item.id}-${item.taskId}`"
        class="pending-item"
        @click="openDetail(item)"
      >
        <div class="item-header">
          <h3>{{ item.title }}</h3>
          <span class="time">{{ item.createTime }}</span>
        </div>
        <div v-if="item.summaryFields.length" class="item-fields">
          <p v-for="field in item.summaryFields" :key="field.key">
            {{ field.key }}：<span v-html="field.value" />
          </p>
        </div>
        <div class="item-status">
          <el-tag size="small" :type="item.statusTagType">
            {{ item.statusLabel }}
          </el-tag>
        </div>
      </div>

      <div v-if="list.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { findPending } from '@/api/process'
import { presentListPage } from '@/work/pending-flow'

export default {
  name: 'PendingList',
  data() {
    return {
      list: [],
      loading: false,
      pageNo: 1,
      pageSize: 10,
      total: 0
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    fetchList() {
      this.loading = true
      findPending(this.pageNo, this.pageSize).then(response => {
        const page = presentListPage(response.data || {})
        this.total = page.total
        this.list = page.records
      }).finally(() => {
        this.loading = false
      })
    },
    handlePageChange(page) {
      this.pageNo = page
      this.fetchList()
    },
    openDetail(item) {
      this.$router.push(item.detailPath)
    }
  }
}
</script>

<style lang="scss" scoped>
.pending-page {
  .page-title {
    margin: 0 0 16px;
    font-size: 20px;
    color: #303133;
  }
}

.pending-item {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: pointer;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

.item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: #303133;
    flex: 1;
  }

  .time {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
}

.item-fields {
  font-size: 14px;
  color: #606266;

  p {
    margin: 4px 0;
    line-height: 1.5;
  }

  ::v-deep img {
    max-width: 100%;
    height: auto;
  }
}

.item-status {
  margin-top: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .item-header {
    flex-direction: column;
  }
}
</style>
