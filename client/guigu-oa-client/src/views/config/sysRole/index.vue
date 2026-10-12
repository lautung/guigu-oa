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

    <el-table v-loading="loading" :data="list" stripe border>
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
        :current-page="page"
        :page-size="limit"
        :page-sizes="[5, 10, 20, 30, 40, 50, 100]"
        :total="total"
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
import { defineComponent } from 'vue'

import { getById, getPageList, removeById, saveOrUpdate } from '@/api/sysRole'
import { assignAuthPath, emptyForm, presentForm, presentListPage } from '@/config/sys-role'
import type { EntityId, RoleListItem } from '@/types/domain'

export default defineComponent({
  name: 'SysRoleList',
  data() {
    return {
      list: [] as RoleListItem[],
      loading: false,
      page: 1,
      limit: 10,
      total: 0,
      roleName: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑角色' : '新建角色'
    },
  },
  created() {
    this.fetchList()
  },
  methods: {
    rowIndex(index: number) {
      return (this.page - 1) * this.limit + index + 1
    },
    searchObj(): Record<string, string | number> {
      return this.roleName ? { roleName: this.roleName } : {}
    },
    fetchList() {
      this.loading = true
      getPageList(this.page, this.limit, this.searchObj())
        .then((response) => {
          const page = presentListPage(response.data || {})
          this.list = page.records
          this.total = page.total
        })
        .finally(() => {
          this.loading = false
        })
    },
    search() {
      this.page = 1
      this.fetchList()
    },
    reset() {
      this.roleName = ''
      this.search()
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
          this.fetchList()
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
          this.fetchList()
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

.pagination-wrap {
  margin-top: 16px;
  text-align: center;
}
</style>
