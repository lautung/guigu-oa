<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <el-form :inline="true" class="filter-form" @submit.native.prevent="search">
      <el-form-item label="关键字">
        <el-input v-model="keyword" placeholder="用户名/姓名/手机号码" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column type="index" label="序号" width="70" align="center" :index="rowIndex" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="phone" label="手机" />
      <el-table-column label="状态" width="90" align="center">
        <template slot-scope="scope">
          <el-switch
            :value="scope.row.statusEnabled"
            @change="toggleStatus(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="220" align="center">
        <template slot-scope="scope">
          <el-button type="text" @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button type="text" @click="openAssignRoles(scope.row)">分配角色</el-button>
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

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="480px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="saving" @click="save">确定</el-button>
      </span>
    </el-dialog>

    <el-dialog title="分配角色" :visible.sync="roleDialogVisible" width="480px">
      <p class="assign-user">用户：{{ assignUser.username }}</p>
      <el-checkbox-group v-model="assignedRoleIds">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <span slot="footer">
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="savingRoles" @click="saveRoles">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getById, getPageList, removeById, saveOrUpdate, updateStatus } from '@/api/sysUser'
import { assignRoles, getRoles } from '@/api/sysRole'
import { emptyForm, presentForm, presentListPage } from '@/config/sys-user'

export default {
  name: 'SysUserList',
  data() {
    return {
      list: [],
      loading: false,
      page: 1,
      limit: 10,
      total: 0,
      keyword: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
      roleDialogVisible: false,
      savingRoles: false,
      assignUser: { id: '', username: '' },
      allRoles: [],
      assignedRoleIds: []
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑用户' : '新建用户'
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    rowIndex(index) {
      return (this.page - 1) * this.limit + index + 1
    },
    searchObj() {
      return this.keyword ? { keyword: this.keyword } : {}
    },
    fetchList() {
      this.loading = true
      getPageList(this.page, this.limit, this.searchObj()).then(response => {
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
      this.keyword = ''
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
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    openEdit(id) {
      getById(id).then(response => {
        this.form = presentForm(response.data || {})
        this.dialogVisible = true
      })
    },
    save() {
      this.saving = true
      saveOrUpdate(this.form).then(response => {
        this.$message.success(response.message || '操作成功')
        this.dialogVisible = false
        this.fetchList()
      }).finally(() => {
        this.saving = false
      })
    },
    remove(id) {
      this.$confirm('此操作将永久删除该用户, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => removeById(id)).then(response => {
        this.$message.success(response.message || '删除成功')
        this.fetchList()
      }).catch(() => {})
    },
    toggleStatus(row) {
      const nextStatus = row.statusEnabled ? 0 : 1
      updateStatus(row.id, nextStatus).then(response => {
        this.$message.success(response.message || '操作成功')
        this.fetchList()
      })
    },
    openAssignRoles(row) {
      this.assignUser = { id: row.id, username: row.username }
      this.roleDialogVisible = true
      getRoles(row.id).then(response => {
        const data = response.data || {}
        this.allRoles = data.allRolesList || []
        this.assignedRoleIds = (data.assginRoleList || []).map(item => item.id)
      })
    },
    saveRoles() {
      this.savingRoles = true
      assignRoles(this.assignUser.id, this.assignedRoleIds).then(response => {
        this.$message.success(response.message || '分配角色成功')
        this.roleDialogVisible = false
        this.fetchList()
      }).finally(() => {
        this.savingRoles = false
      })
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

.assign-user {
  margin: 0 0 12px;
  color: #606266;
}
</style>
