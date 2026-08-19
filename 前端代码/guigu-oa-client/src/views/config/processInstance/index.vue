<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">审批实例查询</h2>
    </div>

    <el-form :inline="true" class="filter-form" @submit.native.prevent="search">
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

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
      <el-table-column prop="processCode" label="审批编号" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="name" label="用户" />
      <el-table-column prop="processTypeName" label="审批类型" />
      <el-table-column prop="processTemplateName" label="审批模板" />
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="90" align="center">
        <template slot-scope="scope">
          <el-button type="text" @click="openDetail(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        :current-page="page"
        :page-size="limit"
        :page-sizes="[5, 10, 20, 30, 40, 50, 100]"
        :total="total"
        layout="sizes, prev, pager, next, jumper, ->, total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script>
import { getPageList } from '@/api/processInstance'
import { emptyQuery, presentListPage, STATUS_OPTIONS } from '@/config/process-instance'

export default {
  name: 'ProcessInstanceList',
  data() {
    return {
      list: [],
      loading: false,
      page: 1,
      limit: 10,
      total: 0,
      query: emptyQuery(),
      createTimes: [],
      statusOptions: STATUS_OPTIONS
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    rowIndex(index) {
      return (this.page - 1) * this.limit + index + 1
    },
    currentQuery() {
      const query = Object.assign({}, this.query)
      if (this.createTimes && this.createTimes.length === 2) {
        query.createTimeBegin = this.createTimes[0]
        query.createTimeEnd = this.createTimes[1]
      } else {
        query.createTimeBegin = ''
        query.createTimeEnd = ''
      }
      return query
    },
    fetchList() {
      this.loading = true
      getPageList(this.page, this.limit, this.currentQuery()).then(response => {
        const page = presentListPage(response.data || {})
        this.list = page.records
        this.total = page.total
      }).finally(() => {
        this.loading = false
      })
    },
    search() {
      this.page = 1
      this.fetchList()
    },
    reset() {
      this.query = emptyQuery()
      this.createTimes = []
      this.search()
    },
    handlePageChange(page) {
      this.page = page
      this.fetchList()
    },
    handleSizeChange(limit) {
      this.limit = limit
      this.page = 1
      this.fetchList()
    },
    openDetail(row) {
      this.$router.push(row.detailPath)
    }
  }
}
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

.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>
