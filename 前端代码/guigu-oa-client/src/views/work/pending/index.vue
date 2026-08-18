<template>
  <div class="pending-page">
    <h2 class="page-title">待处理</h2>

    <div v-loading="loading && list.length === 0" class="list-container">
      <el-empty v-if="!loading && list.length === 0" description="暂无待处理审批" />

      <div
        v-for="item in list"
        :key="`${item.id}-${item.taskId}`"
        class="pending-item"
      >
        <div class="item-header">
          <h3>{{ item.title }}</h3>
          <span class="time">{{ item.createTime }}</span>
        </div>
        <div v-if="item.formValues && item.formValues.formShowData" class="item-fields">
          <p
            v-for="(value, key) in item.formValues.formShowData"
            v-show="key !== '图片'"
            :key="key"
          >
            {{ key }}：<span v-html="value" />
          </p>
        </div>
        <div class="item-status">
          <el-tag size="mini" :type="statusTagType(item.status)">
            {{ statusLabel(item.status) }}
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
    statusLabel(status) {
      if (status === 1) return '审批中'
      if (status === 2) return '审批通过'
      if (status === -1) return '审批拒绝'
      return '未知'
    },
    statusTagType(status) {
      if (status === 1) return 'warning'
      if (status === 2) return 'success'
      if (status === -1) return 'danger'
      return 'info'
    },
    fetchList() {
      this.loading = true
      findPending(this.pageNo, this.pageSize).then(response => {
        const page = response.data
        this.total = page.total || 0
        this.list = (page.records || []).map(item => ({
          ...item,
          formValues: item.formValues ? JSON.parse(item.formValues) : null
        }))
      }).finally(() => {
        this.loading = false
      })
    },
    handlePageChange(page) {
      this.pageNo = page
      this.fetchList()
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
