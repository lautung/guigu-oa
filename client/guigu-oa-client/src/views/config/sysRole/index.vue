<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">角色管理</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
      <el-form-item label="角色名称">
        <el-input v-model="roleName" placeholder="角色名称" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="view.status === 'failed'"
      class="list-error"
      type="error"
      :closable="false"
      :title="view.error || '加载失败'"
    >
      <template #default>
        <el-button type="text" @click="retry">重试</el-button>
      </template>
    </el-alert>

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="view.status === 'ready' ? '暂无数据' : ''"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="roleCode" label="角色编码" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="220" align="center">
        <template #default="scope">
          <el-button type="text" @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button type="text" @click="openAssign(scope.row.id, scope.row.roleName)"
            >菜单授权</el-button
          >
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
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" />
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

import { getById, removeById, saveOrUpdate } from '@/api/sysRole'
import {
  assignAuthPath,
  emptyForm,
  listRequest,
  presentForm,
  presentListPage,
} from '@/config/sys-role'
import { createListRead } from '@/list-read'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, RoleListItem, RoleRecord } from '@/types/domain'
import request from '@/utils/request'

export default defineComponent({
  name: 'SysRoleList',
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const opening = createListRead<RoleRecord, Record<string, string | number>>({
      recognition,
      request,
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      query: {},
    })
    onBeforeUnmount(() => opening.release())
    return { opening }
  },
  data() {
    return {
      roleName: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
    }
  },
  computed: {
    view() {
      return this.opening.view
    },
    rows(): RoleListItem[] {
      return presentListPage({
        records: this.view.records,
        total: this.view.total,
        pages: this.view.pages,
      }).records
    },
    dialogTitle() {
      return this.form.id ? '编辑角色' : '新建角色'
    },
  },
  methods: {
    rowIndex(index: number) {
      return (this.view.page - 1) * this.view.size + index + 1
    },
    searchObj(): Record<string, string | number> {
      return this.roleName ? { roleName: this.roleName } : {}
    },
    search() {
      this.opening.read({ page: 1, query: this.searchObj() })
    },
    reset() {
      this.roleName = ''
      this.search()
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
    openAssign(id: EntityId, roleName?: string) {
      this.$router.push(assignAuthPath(id, roleName))
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
      this.$confirm('此操作将永久删除该角色, 是否继续?', '提示', {
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

.list-error {
  margin-bottom: 12px;
}

.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>
