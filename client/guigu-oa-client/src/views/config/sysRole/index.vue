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

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
      <el-table-column prop="roleName" label="角色名称" />
      <el-table-column prop="roleCode" label="角色编码" />
      <el-table-column prop="createTime" label="创建时间" />
      <el-table-column label="操作" width="300" align="center">
        <template #default="scope">
          <el-button link @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button link @click="openAssign(scope.row.id, scope.row.roleName)">菜单授权</el-button>
          <span class="assign-action">
            <el-button
              v-hasPermi="permissions.assignUser"
              link
              @click="openAssignUsers(scope.row.id, scope.row.roleName)"
              >分配用户</el-button
            >
            <el-tag v-if="isPending(scope.row.id)" type="warning" size="small">待核实</el-tag>
          </span>
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
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option
              v-for="option in dataScopeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.dataScope === 2" label="自定部门" prop="deptIds">
          <el-select
            v-model="form.deptIds"
            multiple
            filterable
            :loading="deptLoading"
            placeholder="选择部门"
            style="width: 100%"
          >
            <el-option
              v-for="dept in deptOptions"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
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

    <RoleUsersDrawer ref="assignRoleDrawer" v-model="assignRoleVisible" :role="assignRole" />
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import { findDeptOptions, getById, removeById, saveOrUpdate } from '@/api/sysRole'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useRoleUsers } from '@/config/role-users-browser'
import {
  DATA_SCOPE_OPTIONS,
  SYS_ROLE_PERMISSIONS,
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListRecord,
} from '@/config/sys-role'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { assignAuthPath } from '@/router/paths'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { DeptOption, EntityId, RoleListItem, RoleRecord } from '@/types/domain'
import type { FormInstance } from 'element-plus'
import RoleUsersDrawer from './RoleUsersDrawer.vue'

interface RoleUsersDrawerHandle {
  requestLeave(): Promise<boolean>
  environmentChanged(): void
}

export default defineComponent({
  name: 'SysRoleList',
  components: { ListLoadError, ListPagination, RoleUsersDrawer },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<RoleRecord, RoleListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...listPageEffects(),
    })
    const roleUsers = useRoleUsers()
    const pendingIds = computed(() => new Set(roleUsers.pendingRoleIds.value))
    const assignRoleDrawer = ref<RoleUsersDrawerHandle>()
    const assignRoleVisible = ref(false)
    const assignRole = ref<{ id: EntityId; roleName?: string } | null>(null)

    function isPending(id: EntityId): boolean {
      return pendingIds.value.has(String(id))
    }

    function environmentChanged(): void {
      assignRoleDrawer.value?.environmentChanged()
    }

    watch(
      () => assignRoleVisible.value,
      (visible, wasVisible) => {
        // 写后刷新：抽屉关闭（成功或结束待确认）后重新读角色列表。
        if (!visible && wasVisible) list.refresh()
      },
    )
    watch(() => userStore.token, environmentChanged)
    onMounted(() => window.addEventListener('focus', environmentChanged))
    onBeforeRouteLeave(async () => (await assignRoleDrawer.value?.requestLeave()) ?? true)
    onBeforeUnmount(() => {
      window.removeEventListener('focus', environmentChanged)
      list.release()
    })
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: SYS_ROLE_PERMISSIONS,
      assignRoleDrawer,
      assignRoleVisible,
      assignRole,
      isPending,
    }
  },
  data() {
    return {
      roleName: '',
      dialogVisible: false,
      saving: false,
      deptLoading: false,
      deptOptions: [] as DeptOption[],
      dataScopeOptions: DATA_SCOPE_OPTIONS,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑角色' : '新建角色'
    },
    formRules() {
      return formRules(this.form.dataScope)
    },
  },
  methods: {
    searchObj(): Record<string, string | number> {
      return this.roleName ? { roleName: this.roleName } : {}
    },
    search() {
      this.list.search(this.searchObj())
    },
    reset() {
      this.roleName = ''
      this.search()
    },
    async loadDeptOptions() {
      this.deptLoading = true
      try {
        const response = await findDeptOptions()
        this.deptOptions = response.data || []
      } catch {
        /* 请求层已提示；部门选项加载失败不阻塞角色表单。 */
      } finally {
        this.deptLoading = false
      }
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
      void this.loadDeptOptions()
    },
    async openEdit(id: EntityId) {
      const response = await this.list.settle(getById(id))
      if (!response) return
      this.form = presentForm(response.data || {})
      this.dialogVisible = true
      void this.loadDeptOptions()
    },
    openAssign(id: EntityId, roleName?: string) {
      this.$router.push(assignAuthPath(id, roleName))
    },
    openAssignUsers(id: EntityId, roleName?: string) {
      this.assignRole = { id, roleName }
      this.assignRoleVisible = true
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
        '此操作将永久删除该角色, 是否继续?',
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

.assign-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
