<template>
  <div class="process-type-page">
    <div class="page-header">
      <h2 class="page-title">审批类型</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="view.status === 'ready' ? '暂无数据' : ''"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
      <el-table-column prop="name" label="类型名称" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作" width="160" align="center">
        <template #default="scope">
          <el-button type="text" @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button type="text" @click="remove(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        :current-page="view.page"
        :page-size="view.size"
        :page-sizes="[5, 10, 20, 30, 40, 50, 100]"
        :total="view.total"
        layout="sizes, prev, pager, next, jumper, ->, total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form label-width="80px">
        <el-form-item label="类型名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="save">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { getById, removeById, saveOrUpdate } from '@/api/processType'
import ListLoadError from '@/components/ListLoadError.vue'
import {
  emptyForm,
  listRequest,
  presentForm,
  presentListPage,
  type ProcessTypeListItem,
} from '@/config/process-type'
import { createListRead, type NoListQuery } from '@/list-read'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, ProcessTypeRecord } from '@/types/domain'
import request from '@/utils/request'

export default defineComponent({
  name: 'ProcessTypeList',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const opening = createListRead<ProcessTypeRecord, NoListQuery>({
      recognition,
      request,
      toRequest: ({ page, size }) => listRequest(page, size),
    })
    onBeforeUnmount(() => opening.release())
    return { opening }
  },
  data() {
    return {
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
    }
  },
  computed: {
    view() {
      return this.opening.view
    },
    rows(): ProcessTypeListItem[] {
      return presentListPage(this.view).records
    },
    dialogTitle() {
      return this.form.id ? '编辑审批类型' : '新建审批类型'
    },
  },
  methods: {
    rowIndex(index: number) {
      return (this.view.page - 1) * this.view.size + index + 1
    },
    retry() {
      this.opening.read()
    },
    handlePageChange(page: number) {
      this.opening.read({ page })
    },
    handleSizeChange(size: number) {
      this.opening.read({ size })
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    openEdit(id: EntityId) {
      getById(id).then((response) => {
        this.form = presentForm(response.data || {})
        this.dialogVisible = true
      })
    },
    save() {
      this.saving = true
      saveOrUpdate(this.form)
        .then((response) => {
          this.$message.success(response.message || '操作成功')
          this.dialogVisible = false
          this.opening.read()
        })
        .finally(() => {
          this.saving = false
        })
    },
    remove(id: EntityId) {
      this.$confirm('此操作将永久删除该审批类型, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
        .then(() => removeById(id))
        .then((response) => {
          this.$message.success(response.message || '删除成功')
          this.opening.read()
        })
        .catch(() => {})
    },
  },
})
</script>

<style lang="scss" scoped>
.process-type-page {
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

.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>
