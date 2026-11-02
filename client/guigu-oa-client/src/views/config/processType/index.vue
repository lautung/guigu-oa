<template>
  <div class="process-type-page">
    <div class="page-header">
      <h2 class="page-title">审批类型</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="name" label="类型名称" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column prop="updateTime" label="更新时间" />
      <el-table-column label="操作" width="160" align="center">
        <template #default="scope">
          <el-button link @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button link @click="remove(scope.row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="类型名称" prop="name">
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
import ListPagination from '@/components/ListPagination.vue'
import {
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListRecord,
  type ProcessTypeListItem,
} from '@/config/process-type'
import { createListPage } from '@/list-page'
import { listPageEffects } from '@/list-page-browser'
import type { NoListQuery } from '@/list-read'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, ProcessTypeRecord } from '@/types/domain'
import type { FormInstance } from 'element-plus'

export default defineComponent({
  name: 'ProcessTypeList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<ProcessTypeRecord, ProcessTypeListItem, NoListQuery>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => listRequest(page, size),
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
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
      formRules: formRules(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑审批类型' : '新建审批类型'
    },
  },
  methods: {
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    async openEdit(id: EntityId) {
      const response = await this.list.settle(getById(id))
      if (!response) return
      this.form = presentForm(response.data || {})
      this.dialogVisible = true
    },
    validateForm(): Promise<boolean> {
      const form = this.$refs.formRef as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async save() {
      if (!(await this.validateForm())) return
      this.saving = true
      try {
        if (await this.list.runWrite(() => saveOrUpdate(this.form), '操作成功'))
          this.dialogVisible = false
      } finally {
        this.saving = false
      }
    },
    remove(id: EntityId) {
      return this.list.confirmRemove(
        '此操作将永久删除该审批类型, 是否继续?',
        () => removeById(id),
        '删除成功',
      )
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
</style>
