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
      <el-table-column label="操作" width="250" align="center">
        <template #default="scope">
          <el-button type="text" @click="openEdit(scope.row.id)">编辑</el-button>
          <span class="assign-action">
            <el-button type="text" @click="openAssignRoles(scope.row.id, scope.row.username)"
              >分配角色</el-button
            >
            <el-tag v-if="isPending(scope.row.id)" type="warning" size="small">待核实</el-tag>
          </span>
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

    <AssignRolesDialog ref="assignRolesDialog" v-model="assignRolesVisible" :user="assignUser" />
  </div>
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, defineComponent } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import { getById, getPageList, removeById, saveOrUpdate, updateStatus } from '@/api/sysUser'
import { useAssignRoles } from '@/config/assign-roles-browser'
import { emptyForm, presentForm, presentListPage } from '@/config/sys-user'
import { ROUTES } from '@/navigation/constants'
import { useUserStore } from '@/store/modules/user'
import { resolveClientSurface } from '@/surface/client-surface'
import type { EntityId, UserListItem } from '@/types/domain'
import AssignRolesDialog from './AssignRolesDialog.vue'

interface AssignRolesDialogHandle {
  requestLeave(force?: boolean): Promise<boolean>
}

export default defineComponent({
  name: 'SysUserList',
  components: { AssignRolesDialog },
  setup() {
    const assignRolesDialog = ref<AssignRolesDialogHandle>()
    const assignRolesVisible = ref(false)
    const assignUser = ref<{ id: EntityId; username: string } | null>(null)
    const assignRoles = useAssignRoles()
    const pendingIds = computed(() => new Set(assignRoles.pendingUserIds.value))
    const userStore = useUserStore()
    const router = useRouter()
    const route = useRoute()

    function isPending(id: EntityId): boolean {
      return pendingIds.value.has(String(id))
    }

    async function requestAssignRolesLeave(force = false): Promise<boolean> {
      return (await assignRolesDialog.value?.requestLeave(force)) ?? true
    }

    async function openAssignRoles(id: EntityId, username?: string): Promise<void> {
      if (assignRolesVisible.value) {
        const left = await requestAssignRolesLeave()
        if (!left) return
      }
      assignUser.value = { id, username: username || '' }
      assignRolesVisible.value = true
    }

    function mustLeave(): boolean {
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

    async function enforceEntry(): Promise<void> {
      if (!mustLeave()) return
      await requestAssignRolesLeave(true)
      ElMessageBox.close()
      if (userStore.token) void router.replace(ROUTES.PENDING)
    }

    watch(() => [userStore.token, userStore.menus] as const, enforceEntry, { deep: true })
    onBeforeRouteLeave(async () => requestAssignRolesLeave(mustLeave()))
    onMounted(() => {
      window.addEventListener('resize', enforceEntry)
      window.addEventListener('focus', enforceEntry)
    })
    onBeforeUnmount(() => {
      window.removeEventListener('resize', enforceEntry)
      window.removeEventListener('focus', enforceEntry)
    })

    return {
      assignRolesDialog,
      assignRolesVisible,
      assignUser,
      isPending,
      openAssignRoles,
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

.assign-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
