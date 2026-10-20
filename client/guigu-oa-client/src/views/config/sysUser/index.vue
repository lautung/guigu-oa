<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建</el-button>
    </div>

    <el-form :inline="true" class="filter-form" @submit.prevent="search">
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
        <template #default="scope">
          <el-switch
            :value="scope.row.statusEnabled"
            @change="toggleStatus(scope.row.id, scope.row.statusEnabled)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="220" align="center">
        <template #default="scope">
          <el-button type="text" @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button type="text" @click="openAssignRoles(scope.row.id, scope.row.username)"
            >分配角色</el-button
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
      <template #footer>
        <div>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="saving" @click="save">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <div v-if="roleState.permissionRefresh.status === 'retryable'" class="role-result" role="alert">
      <p>{{ roleState.permissionRefresh.error }}</p>
      <el-button @click="assignment.retryRecognition()">重试权限刷新</el-button>
    </div>
    <el-dialog
      :model-value="roleState.phase !== 'closed'"
      title="分配角色"
      width="560px"
      :before-close="closeRoles"
    >
      <p class="assign-user">
        用户：{{ roleState.target?.username }}（{{ roleState.target?.id }}）
      </p>
      <p v-if="roleState.reading" role="status">正在读取角色，完成前不能保存。</p>
      <p v-if="roleState.phase === 'saving'" role="status">
        正在保存分配角色，本次提交内容已固定。离开不撤销已提交的保存。
      </p>
      <div v-if="roleState.phase === 'uncertain'" class="role-result" role="alert">
        <h3>分配结果待确认</h3>
        <p>本次提交已保留，暂停再次保存。当前角色与原提交相同或不同都不能证明原保存的结果。</p>
        <el-button
          :disabled="roleState.reading || roleState.leaving || !!roleState.recordError"
          @click="assignment.reload()"
          >重新读取当前角色</el-button
        >
        <el-button
          type="warning"
          :disabled="roleState.leaving || !!roleState.recordError"
          @click="assignment.endAssignment()"
          >结束本次分配</el-button
        >
      </div>
      <p v-if="roleState.snapshot" class="submitted-roles">
        本次提交角色：{{ roleState.submittedRoleNames || '无角色（清空全部）' }}
      </p>
      <p v-if="roleState.currentRoles !== null" class="current-roles">
        当前已保存角色：{{
          roleState.currentRoles.map((role) => role.roleName).join('、') || '无角色'
        }}
      </p>
      <div v-if="roleState.error || roleState.recordError" role="alert">
        <p>{{ roleState.recordError || roleState.error }}</p>
        <el-button
          v-if="roleState.phase === 'load-error' && !roleState.recordError"
          @click="assignment.reload()"
          >重新读取角色</el-button
        >
      </div>
      <p v-if="roleState.phase === 'saved'">分配已保存。</p>
      <el-checkbox-group
        :model-value="[...roleState.selectedIds]"
        :disabled="!roleState.canEdit"
        @update:model-value="selectRoles"
      >
        <el-checkbox v-for="role in roleState.roles" :key="role.id" :value="role.id">{{
          role.roleName
        }}</el-checkbox>
      </el-checkbox-group>
      <p v-if="roleState.phase === 'ready' && roleState.roles.length === 0">
        当前没有可选角色，可保存空角色集合。
      </p>
      <template #footer>
        <el-button @click="assignment.requestLeave()">关闭</el-button>
        <el-button
          v-if="roleState.phase === 'saved'"
          :disabled="!!roleState.recordError"
          @click="assignment.reload()"
          >重新读取角色</el-button
        >
        <el-button type="primary" :disabled="!roleState.canSave" @click="assignment.save()"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, onBeforeUnmount, onMounted, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useRoleAssignment } from '@/config/role-assignment-browser'
import { useUserStore } from '@/store/modules/user'
import { resolveClientSurface } from '@/surface/client-surface'

import { getById, getPageList, removeById, saveOrUpdate, updateStatus } from '@/api/sysUser'
import { emptyForm, presentForm, presentListPage } from '@/config/sys-user'
import type { EntityId, UserListItem } from '@/types/domain'

export default defineComponent({
  name: 'SysUserList',
  setup() {
    const userStore = useUserStore(),
      route = useRoute(),
      router = useRouter()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const assignment = useRoleAssignment(recognition)
    function mustLeave() {
      return (
        !userStore.token ||
        resolveClientSurface({
          path: route.path,
          hasToken: !!userStore.token,
          menus: userStore.menus,
          viewportWidth: window.innerWidth,
        }).occupancy.action === 'redirect'
      )
    }
    function enforceEntry() {
      if (mustLeave() || !assignment.state.isCurrent) {
        assignment.release()
        if (userStore.token) void router.replace('/work/pending')
      }
    }
    onBeforeRouteLeave(() => assignment.requestLeave(mustLeave()))
    watch(() => [userStore.token, userStore.menus] as const, enforceEntry, { deep: true })
    onMounted(() => {
      window.addEventListener('resize', enforceEntry)
      window.addEventListener('focus', enforceEntry)
    })
    onBeforeUnmount(() => {
      assignment.release()
      window.removeEventListener('resize', enforceEntry)
      window.removeEventListener('focus', enforceEntry)
    })
    return {
      assignment,
      roleState: assignment.state,
      closeRoles: async (done: () => void) => {
        if (await assignment.requestLeave()) done()
      },
      selectRoles: (ids: (string | number | boolean)[]) =>
        assignment.select(
          ids.filter((id): id is EntityId => typeof id === 'string' || typeof id === 'number'),
        ),
    }
  },
  data() {
    return {
      list: [] as UserListItem[],
      loading: false,
      page: 1,
      limit: 10,
      total: 0,
      keyword: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑用户' : '新建用户'
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
      return this.keyword ? { keyword: this.keyword } : {}
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
      this.keyword = ''
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
      this.$confirm('此操作将永久删除该用户, 是否继续?', '提示', {
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
    toggleStatus(id: EntityId, statusEnabled: boolean) {
      const nextStatus = statusEnabled ? 0 : 1
      updateStatus(id, nextStatus).then((response) => {
        this.$message.success(response.message || '操作成功')
        this.fetchList()
      })
    },
    openAssignRoles(id: EntityId, username?: string) {
      void this.assignment.open({ id, username: username || '' })
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

.role-result {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #e6a23c;
  border-radius: 6px;
}

.assign-user {
  margin: 0 0 12px;
  color: #606266;
}
</style>
