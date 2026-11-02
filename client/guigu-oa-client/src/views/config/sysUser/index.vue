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

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      stripe
      border
    >
      <el-table-column type="index" label="序号" width="70" align="center" :index="list.rowIndex" />
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
          <el-button link @click="openEdit(scope.row.id)">编辑</el-button>
          <span class="assign-action">
            <el-button link @click="openAssignRoles(scope.row.id, scope.row.username)"
              >分配角色</el-button
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
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
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
import { onBeforeRouteLeave } from 'vue-router'

import { getById, removeById, saveOrUpdate, updateStatus } from '@/api/sysUser'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useAssignRoles } from '@/config/assign-roles-browser'
import {
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListRecord,
} from '@/config/sys-user'
import { createListPage } from '@/list-page'
import { listPageEffects } from '@/list-page-browser'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, UserListItem, UserRecord } from '@/types/domain'
import type { FormInstance } from 'element-plus'
import AssignRolesDialog from './AssignRolesDialog.vue'

interface AssignRolesDialogHandle {
  requestLeave(): Promise<boolean>
  environmentChanged(): void
}

export default defineComponent({
  name: 'SysUserList',
  components: { AssignRolesDialog, ListLoadError, ListPagination },
  setup() {
    const assignRolesDialog = ref<AssignRolesDialogHandle>()
    const assignRolesVisible = ref(false)
    const assignUser = ref<{ id: EntityId; username: string } | null>(null)
    const assignRoles = useAssignRoles()
    const pendingIds = computed(() => new Set(assignRoles.pendingUserIds.value))
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<UserRecord, UserListItem, Record<string, string | number>>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size, query }) => listRequest(page, size, query),
      present: presentListRecord,
      ...listPageEffects(),
    })

    function isPending(id: EntityId): boolean {
      return pendingIds.value.has(String(id))
    }

    async function requestAssignRolesLeave(): Promise<boolean> {
      return (await assignRolesDialog.value?.requestLeave()) ?? true
    }

    function environmentChanged(): void {
      assignRolesDialog.value?.environmentChanged()
    }

    async function openAssignRoles(id: EntityId, username?: string): Promise<void> {
      if (assignRolesVisible.value) {
        const left = await requestAssignRolesLeave()
        if (!left) return
      }
      assignUser.value = { id, username: username || '' }
      assignRolesVisible.value = true
    }

    watch(() => [userStore.token, userStore.menus] as const, environmentChanged, { deep: true })
    onBeforeRouteLeave(async () => requestAssignRolesLeave())
    onMounted(() => {
      window.addEventListener('resize', environmentChanged)
      window.addEventListener('focus', environmentChanged)
    })
    onBeforeUnmount(() => {
      list.release()
      window.removeEventListener('resize', environmentChanged)
      window.removeEventListener('focus', environmentChanged)
    })

    return {
      assignRolesDialog,
      assignRolesVisible,
      assignUser,
      isPending,
      openAssignRoles,
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
    }
  },
  data() {
    return {
      keyword: '',
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
      formRules: formRules(),
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑用户' : '新建用户'
    },
  },
  methods: {
    searchObj(): Record<string, string | number> {
      return this.keyword ? { keyword: this.keyword } : {}
    },
    search() {
      this.list.search(this.searchObj())
    },
    reset() {
      this.keyword = ''
      this.search()
    },
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
        '此操作将永久删除该用户, 是否继续?',
        () => removeById(id),
        '删除成功',
      )
    },
    toggleStatus(id: EntityId, statusEnabled: boolean) {
      const nextStatus = statusEnabled ? 0 : 1
      return this.list.runWrite(() => updateStatus(id, nextStatus), '操作成功')
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
