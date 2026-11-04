<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">岗位管理</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="岗位名称">
        <el-input v-model="name" placeholder="岗位名称" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="postCode" label="岗位编码" />
      <el-table-column prop="name" label="岗位名称" />
      <el-table-column prop="sortValue" label="排序" width="90" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
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
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="岗位编码" prop="postCode">
          <el-input v-model="form.postCode" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortValue" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="save">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount } from 'vue'

import { removeById, saveOrUpdate } from '@/api/sysPost'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import {
  POST_STATUS_OPTIONS,
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListRecord,
} from '@/config/sys-post'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, PostListItem, PostRecord } from '@/types/domain'
import type { FormInstance } from 'element-plus'

export default defineComponent({
  name: 'SysPostList',
  components: { ListLoadError, ListPagination },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<PostRecord, PostListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
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
      name: '',
      dialogVisible: false,
      saving: false,
      statusOptions: POST_STATUS_OPTIONS,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑岗位' : '新建岗位'
    },
    formRules() {
      return formRules()
    },
  },
  methods: {
    searchObj(): Record<string, string | number> {
      return this.name ? { name: this.name } : {}
    },
    search() {
      this.list.search(this.searchObj())
    },
    reset() {
      this.name = ''
      this.search()
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    openEdit(id: EntityId) {
      const record = this.rows.find((row) => String(row.id) === String(id))
      if (!record) return
      this.form = presentForm(record)
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
        '此操作将永久删除该岗位, 是否继续?',
        () => removeById(id),
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
</style>
