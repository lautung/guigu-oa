<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <div class="page-actions">
        <el-button
          v-hasPermi="permissions.add"
          type="primary"
          icon="el-icon-plus"
          @click="openCreate"
          >新建</el-button
        >
        <el-button v-hasPermi="permissions.export" @click="handleExport">导出</el-button>
        <el-button v-hasPermi="permissions.import" @click="openImport">导入</el-button>
      </div>
    </div>

    <div class="user-layout">
      <aside class="dept-filter">
        <el-tree
          ref="deptTreeRef"
          :data="deptTree"
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleDeptClick"
        />
      </aside>

      <section class="user-main">
        <el-form :inline="true" class="filter-form" @submit.prevent="search">
          <el-form-item label="关键字">
            <el-input v-model="keyword" placeholder="用户名" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="statusFilter" clearable placeholder="全部" style="width: 120px">
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
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
          <el-table-column
            type="index"
            label="序号"
            width="70"
            align="center"
            :index="list.rowIndex"
          />
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
          <el-table-column label="操作" width="330" align="center">
            <template #default="scope">
              <el-button v-hasPermi="permissions.edit" link @click="openEdit(scope.row.id)"
                >编辑</el-button
              >
              <span class="assign-action">
                <el-button
                  v-hasPermi="permissions.assignRole"
                  link
                  @click="openAssignRoles(scope.row.id, scope.row.username)"
                  >分配角色</el-button
                >
                <el-tag v-if="isPending(scope.row.id)" type="warning" size="small">待核实</el-tag>
              </span>
              <el-button
                v-hasPermi="permissions.resetPassword"
                link
                @click="openResetPassword(scope.row.id, scope.row.username)"
                >重置密码</el-button
              >
              <el-button v-hasPermi="permissions.remove" link @click="remove(scope.row.id)"
                >删除</el-button
              >
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
      </section>
    </div>

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
        <el-form-item label="部门">
          <!-- update 的「空=不覆盖」语义没有「清空部门」入口，所以只有新建可以清除。 -->
          <el-tree-select
            v-model="form.deptId"
            :data="deptFormTree"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            node-key="id"
            check-strictly
            :clearable="!form.id"
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.postIds" multiple clearable style="width: 100%">
            <el-option
              v-for="post in postOptions"
              :key="post.id"
              :label="post.name"
              :value="post.id"
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

    <el-dialog
      v-model="resetDialogVisible"
      :title="resetDialogTitle"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="resetFormRef"
        :model="resetForm"
        :rules="resetRules"
        label-width="90px"
        autocomplete="off"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="resetDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="resetting" @click="saveResetPassword"
            >确定</el-button
          >
        </div>
      </template>
    </el-dialog>

    <AssignRolesDialog ref="assignRolesDialog" v-model="assignRolesVisible" :user="assignUser" />

    <el-dialog v-model="importDialogVisible" title="导入用户" width="720px" destroy-on-close>
      <div class="import-toolbar">
        <el-button link type="primary" @click="handleDownloadTemplate">下载模板</el-button>
        <input
          ref="importFileInput"
          class="import-file-input"
          type="file"
          :accept="importAccept"
          @change="handleImportFileChange"
        />
      </div>
      <p class="import-hint">
        仅支持 xlsx，单个文件不超过 2 MB；导入用户的初始密码为 {{ importDefaultPassword }}。
        失败行不会写入，修正后可重新导入。
      </p>
      <p v-if="importError" class="import-error">{{ importError }}</p>
      <template v-if="importResult">
        <p class="import-summary">{{ importSummary }}</p>
        <el-table
          v-if="importResult.failures.length"
          :data="importResult.failures"
          size="small"
          border
          class="import-failures"
        >
          <el-table-column prop="rowNumber" label="行号" width="80" align="center" />
          <el-table-column prop="username" label="用户名" width="170" />
          <el-table-column prop="reason" label="失败原因" />
        </el-table>
        <p v-else class="import-all-success">全部导入成功。</p>
      </template>
      <template #footer>
        <div>
          <el-button @click="importDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="!importFile"
            @click="submitImport"
            >开始导入</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, defineComponent } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

import {
  downloadImportTemplate,
  exportUsers,
  findDeptTree,
  findPostOptions,
  getById,
  importUsers,
  removeById,
  resetPassword,
  saveOrUpdate,
  updateStatus,
} from '@/api/sysUser'
import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { useAssignRoles } from '@/config/assign-roles-browser'
import {
  ALL_DEPTS_ID,
  emptyForm,
  emptyResetPasswordForm,
  formRules,
  listRequest,
  presentDeptFilterTree,
  presentDeptFormTree,
  presentForm,
  presentListRecord,
  presentPostOptions,
  resetPasswordRules,
  SYS_USER_PERMISSIONS,
  USER_STATUS_OPTIONS,
} from '@/config/sys-user'
import {
  downloadBlob,
  exportFileName,
  IMPORT_ACCEPT,
  IMPORT_DEFAULT_PASSWORD,
  IMPORT_TEMPLATE_FILE_NAME,
  importResultSummary,
  presentImportResult,
  SYS_USER_IO_PERMISSIONS,
  validateImportFile,
} from '@/config/user-export'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { failureMessage } from '@/request/request-failure'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type {
  DeptRecord,
  DeptSelectNode,
  EntityId,
  PostOption,
  UserImportResultView,
  UserListItem,
  UserRecord,
} from '@/types/domain'
import type { FormInstance } from 'element-plus'
import AssignRolesDialog from './AssignRolesDialog.vue'

interface AssignRolesDialogHandle {
  requestLeave(): Promise<boolean>
  environmentChanged(): void
}

interface DeptTreeHandle {
  setCurrentKey(key?: EntityId): void
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

    watch(() => userStore.token, environmentChanged)
    onBeforeRouteLeave(async () => requestAssignRolesLeave())
    onMounted(() => window.addEventListener('focus', environmentChanged))
    onBeforeUnmount(() => {
      list.release()
      window.removeEventListener('focus', environmentChanged)
    })

    return {
      assignRolesDialog,
      assignRolesVisible,
      assignUser,
      isPending,
      openAssignRoles,
      permissions: { ...SYS_USER_PERMISSIONS, ...SYS_USER_IO_PERMISSIONS },
      statusOptions: USER_STATUS_OPTIONS,
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
    }
  },
  data() {
    // 表单对象身份保持稳定：resetRules 的闭包与 el-form 的 model 必须指向同一个对象。
    const resetForm = emptyResetPasswordForm()
    return {
      keyword: '',
      statusFilter: '' as '' | number,
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
      formRules: formRules(),
      // 只读部门树（含「全部」）与岗位选项；维护入口仍在部门/岗位管理页。
      deptRecords: [] as DeptRecord[],
      postOptions: [] as PostOption[],
      currentDeptId: ALL_DEPTS_ID as EntityId,
      resetDialogVisible: false,
      resetting: false,
      resetTarget: null as { id: EntityId; username?: string } | null,
      resetForm,
      resetRules: resetPasswordRules(() => resetForm),
      importDialogVisible: false,
      importing: false,
      importFile: null as File | null,
      importResult: null as UserImportResultView | null,
      importError: '',
      importAccept: IMPORT_ACCEPT,
      importDefaultPassword: IMPORT_DEFAULT_PASSWORD,
    }
  },
  computed: {
    deptTree(): DeptSelectNode[] {
      return presentDeptFilterTree(this.deptRecords)
    },
    deptFormTree(): DeptSelectNode[] {
      return presentDeptFormTree(this.deptRecords)
    },
    dialogTitle() {
      return this.form.id ? '编辑用户' : '新建用户'
    },
    resetDialogTitle() {
      const username = this.resetTarget?.username
      return username ? `重置密码 — ${username}` : '重置密码'
    },
    importSummary(): string {
      return this.importResult ? importResultSummary(this.importResult) : ''
    },
  },
  mounted() {
    void this.loadOptions()
  },
  methods: {
    /** 选项读失败不阻塞列表：请求层已提示，空选项只影响筛选与表单的可选范围。 */
    async loadOptions() {
      const [depts, posts] = await Promise.all([
        this.list.settle(findDeptTree()),
        this.list.settle(findPostOptions()),
      ])
      if (depts) this.deptRecords = depts.data || []
      if (posts) this.postOptions = presentPostOptions(posts.data || [])
    },
    searchObj(): Record<string, string | number> {
      const query: Record<string, string | number> = {}
      if (this.keyword) query.keyword = this.keyword
      if (this.currentDeptId !== ALL_DEPTS_ID) query.deptId = this.currentDeptId
      if (this.statusFilter !== '') query.status = this.statusFilter
      return query
    },
    search() {
      this.list.search(this.searchObj())
    },
    reset() {
      this.keyword = ''
      this.statusFilter = ''
      this.currentDeptId = ALL_DEPTS_ID
      this.syncDeptSelection()
      this.search()
    },
    /** 导出与列表共用当前筛选；失败由请求层上屏，不弹第二遍。 */
    async handleExport() {
      const blob = await this.list.settle(exportUsers(this.searchObj()))
      if (blob) downloadBlob(blob, exportFileName())
    },
    /** 导入弹窗每次打开都清掉上一次的文件与汇总，避免把旧结果当成新导入。 */
    openImport() {
      this.importFile = null
      this.importResult = null
      this.importError = ''
      this.importDialogVisible = true
      const input = this.$refs.importFileInput as HTMLInputElement | undefined
      if (input) input.value = ''
    },
    /** 模板下载失败只在弹窗内显示一次：请求层静默，不再弹全局提示。 */
    async handleDownloadTemplate() {
      const blob = await this.list.settle(downloadImportTemplate({ notifyNormalErrors: false }))
      if (blob) {
        downloadBlob(blob, IMPORT_TEMPLATE_FILE_NAME)
      } else {
        this.importError = '模板下载失败，请重试'
      }
    },
    /** 选择文件先走本地预检；被拒的文件不留在输入框里，也不发请求。 */
    handleImportFileChange(event: Event) {
      const input = event.target as HTMLInputElement | null
      const file = input?.files?.[0] ?? null
      this.importResult = null
      this.importError = ''
      const reason = validateImportFile(file)
      if (reason) {
        this.importFile = null
        this.importError = reason
        if (input) input.value = ''
        return
      }
      this.importFile = file
    },
    /**
     * 逐行导入的部分成功：结果原样展示成功数与失败行，不把部分成功说成整体失败；
     * 只有成功行 > 0 才刷新列表，失败行在弹窗里等用户修正。
     */
    async submitImport() {
      const file = this.importFile
      if (!file) return
      const reason = validateImportFile(file)
      if (reason) {
        this.importError = reason
        return
      }
      this.importing = true
      this.importError = ''
      try {
        // 导入失败文案在弹窗内展示；请求层静默，避免同一条失败提示两次。
        const response = await importUsers(file, { notifyNormalErrors: false })
        this.importResult = presentImportResult(response.data)
        if (this.importResult.successCount > 0) this.list.refresh()
      } catch (failure) {
        this.importError = failureMessage(failure) || '导入失败，请重试'
      } finally {
        this.importing = false
      }
    },
    /** 左树点击（#249）：选中部门过滤，再点同一部门清除；「全部」节点等价于清除。 */
    handleDeptClick(node?: { id?: EntityId }) {
      const clicked = node?.id ?? ALL_DEPTS_ID
      const clearing = clicked === ALL_DEPTS_ID || String(clicked) === String(this.currentDeptId)
      this.currentDeptId = clearing ? ALL_DEPTS_ID : clicked
      this.syncDeptSelection()
      this.list.search(this.searchObj())
    },
    syncDeptSelection() {
      void this.$nextTick(() => {
        const tree = this.$refs.deptTreeRef as DeptTreeHandle | undefined
        tree?.setCurrentKey?.(this.currentDeptId)
      })
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
    openResetPassword(id: EntityId, username?: string) {
      this.resetTarget = { id, username }
      // 保持表单对象身份稳定：el-form 在首次打开时已持有它，整体替换会让校验读到旧字段。
      Object.assign(this.resetForm, emptyResetPasswordForm())
      this.resetDialogVisible = true
    },
    validateResetForm(): Promise<boolean> {
      const form = this.$refs.resetFormRef as FormInstance | undefined
      if (!form) return Promise.resolve(false)
      return form
        .validate()
        .then(() => true)
        .catch(() => false)
    },
    async saveResetPassword() {
      const target = this.resetTarget
      if (!target) return
      if (!(await this.validateResetForm())) return
      this.resetting = true
      try {
        const reset = this.list.runWrite(
          () => resetPassword(target.id, this.resetForm.newPassword),
          '重置密码成功',
        )
        if (await reset) this.resetDialogVisible = false
      } finally {
        this.resetting = false
      }
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

.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.user-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.dept-filter {
  flex: 0 0 220px;
  min-height: 320px;
  padding-right: 12px;
  border-right: 1px solid #e4e7ed;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.filter-form {
  margin-bottom: 12px;
}

.assign-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.import-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.import-hint {
  margin: 8px 0;
  color: #909399;
  font-size: 13px;
}

.import-error {
  margin: 8px 0;
  color: #f56c6c;
}

.import-summary {
  margin: 12px 0 8px;
  font-weight: 600;
}

.import-all-success {
  margin: 8px 0;
  color: #67c23a;
}
</style>
