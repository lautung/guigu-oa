<template>
  <div class="process-template-page">
    <div class="page-header">
      <h2 class="page-title">审批模板</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
      <el-table-column prop="name" label="审批名称" />
      <el-table-column label="图标" width="80" align="center">
        <template #default="scope">
          <img v-if="scope.row.iconUrl" :src="scope.row.iconUrl" class="template-icon" alt="" />
        </template>
      </el-table-column>
      <el-table-column prop="processTypeName" label="审批类型" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="状态" width="220">
        <template #default="scope">
          <div>{{ scope.row.statusLabel }}</div>
          <small v-if="scope.row.availabilityMessage" class="availability-message">
            {{ scope.row.availabilityMessage }}
          </small>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作" width="260" align="center">
        <template #default="scope">
          <el-button type="text" @click="show(scope.row)">查看</el-button>
          <el-button v-if="scope.row.canEdit" type="text" @click="openEdit(scope.row.id)"
            >编辑</el-button
          >
          <el-button v-if="scope.row.canDelete" type="text" @click="remove(scope.row)"
            >删除</el-button
          >
          <el-button v-if="scope.row.canPublish" type="text" @click="publishTemplate(scope.row)"
            >发布</el-button
          >
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

    <el-dialog v-model="previewVisible" title="查看审批模板" width="520px">
      <h3>基本信息</h3>
      <el-divider />
      <el-form label-width="90px">
        <el-form-item label="审批类型">{{ preview.processTypeName }}</el-form-item>
        <el-form-item label="名称">{{ preview.name }}</el-form-item>
        <el-form-item label="创建时间">{{ preview.createTime }}</el-form-item>
      </el-form>
      <h3>表单信息</h3>
      <el-divider />
      <form-create v-if="previewVisible" :rule="previewRule" :option="previewOption" />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

import request from '@/utils/request'
import {
  findPage,
  presentDesignerState,
  publish,
  removeUnpublished,
} from '@/config/process-template'
import type {
  FormDesignerOption,
  FormRule,
  ProcessTemplateListItem,
  ProcessTemplateRecord,
} from '@/types/domain'

export default defineComponent({
  name: 'ProcessTemplateList',
  data() {
    return {
      list: [] as ProcessTemplateListItem[],
      loading: false,
      page: 1,
      limit: 10,
      total: 0,
      previewVisible: false,
      preview: {} as ProcessTemplateRecord,
      previewRule: [] as FormRule[],
      previewOption: { submitBtn: false } as FormDesignerOption,
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    rowIndex(index: number) {
      return (this.page - 1) * this.limit + index + 1
    },
    fetchList() {
      this.loading = true
      findPage(this.page, this.limit, request)
        .then((page) => {
          this.list = page.records
          this.total = page.total
        })
        .finally(() => {
          this.loading = false
        })
    },
    handlePageChange(page: number) {
      this.page = page
      this.fetchList()
    },
    handleSizeChange(limit: number) {
      this.limit = limit
      this.page = 1
      this.fetchList()
    },
    openCreate() {
      this.$router.push('/processSet/templateSet')
    },
    openEdit(id: string | number | undefined) {
      if (id == null) return
      this.$router.push({ path: '/processSet/templateSet', query: { id } })
    },
    show(row: ProcessTemplateRecord) {
      this.preview = row
      const design = presentDesignerState(row)
      this.previewRule = design.rule
      this.previewOption = Object.assign({ submitBtn: false }, design.option)
      this.previewVisible = true
    },
    publishTemplate(template: ProcessTemplateRecord) {
      publish(template, request).then((response) => {
        this.$message.success(response.message || '发布成功')
        this.fetchList()
      })
    },
    remove(template: ProcessTemplateRecord) {
      this.$confirm('此操作将永久删除该审批模板, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => removeUnpublished(template, request))
        .then((response) => {
          this.$message.success(response.message || '删除成功')
          this.fetchList()
        })
        .catch(() => {})
    },
  },
})
</script>

<style lang="scss" scoped>
.process-template-page {
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

.template-icon {
  width: 30px;
  height: 30px;
  vertical-align: middle;
}

.availability-message {
  color: #b88230;
}

.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>
