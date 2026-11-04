<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">部门管理</h2>
      <el-button
        v-hasPermi="permissions.add"
        type="primary"
        icon="el-icon-plus"
        @click="openCreate()"
      >
        新建
      </el-button>
    </div>

    <ListLoadError :status="view.status" :error="view.error" @retry="list.retry" />

    <el-table
      v-loading="view.status === 'loading'"
      :data="rows"
      :empty-text="emptyText"
      row-key="id"
      border
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="name" label="部门名称" min-width="200" />
      <el-table-column prop="leader" label="负责人" width="140" />
      <el-table-column prop="sortValue" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="250" align="center">
        <template #default="scope">
          <el-button v-hasPermi="permissions.add" link @click="openCreate(scope.row)">
            新增下级
          </el-button>
          <el-button v-hasPermi="permissions.edit" link @click="openEdit(scope.row)"
            >编辑</el-button
          >
          <el-button v-hasPermi="permissions.remove" link @click="remove(scope.row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门" prop="parentId">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option
              v-for="option in parentChoices"
              :key="option.id"
              :label="optionLabel(option)"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortValue" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio>
          </el-radio-group>
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

import { removeById, saveOrUpdate } from '@/api/sysDept'
import ListLoadError from '@/components/ListLoadError.vue'
import {
  DEPT_PERMISSIONS,
  DEPT_STATUS_OPTIONS,
  emptyForm,
  findTreeRequest,
  formRules,
  parentOptions,
  presentDeptNode,
  presentForm,
} from '@/config/sys-dept'
import { createListPage } from '@/list/list-page'
import { listPageEffects } from '@/list/list-page-browser'
import { PermissionRequestFailure, failureMessage } from '@/request/request-failure'
import { openRequestScope } from '@/request/request-scope'
import { useUserStore } from '@/store/modules/user'
import type { DeptForm, DeptParentOption, DeptRecord, DeptTreeNode, EntityId } from '@/types/domain'
import type { FormInstance } from 'element-plus'

const effects = listPageEffects()

/**
 * 系统管理 > 部门管理（#247）：树表格、新增/编辑弹窗与删除确认。
 * 读是整表读（`fetchAll`）——服务端直接给根节点数组；写后刷新、失败策略与
 * 删除确认仍走 `createListPage`。删除保护的两个 201 文案由本页回显。
 */
export default defineComponent({
  name: 'SysDeptList',
  components: { ListLoadError },
  setup() {
    const userStore = useUserStore()
    const recognition = userStore.recognition
    if (!recognition) throw new Error('认出 module 尚未接入')
    const list = createListPage<DeptRecord, DeptTreeNode>({
      openScope: () => openRequestScope(recognition, { notifyNormalErrors: false }),
      toRequest: () => findTreeRequest(),
      present: presentDeptNode,
      fetchAll: true,
      ...effects,
    })
    onBeforeUnmount(() => list.release())
    return {
      list,
      view: list.view,
      rows: list.rows,
      emptyText: list.emptyText,
      permissions: DEPT_PERMISSIONS,
    }
  },
  data() {
    return {
      dialogVisible: false,
      saving: false,
      form: emptyForm(),
      statusOptions: DEPT_STATUS_OPTIONS,
    }
  },
  computed: {
    dialogTitle(): string {
      return this.form.id ? '编辑部门' : '新建部门'
    },
    rules() {
      return formRules()
    },
    parentChoices(): DeptParentOption[] {
      return parentOptions(this.rows, this.form.id || undefined)
    },
  },
  methods: {
    statusLabel(status?: number): string {
      return DEPT_STATUS_OPTIONS.find((option) => option.value === status)?.label || ''
    },
    optionLabel(option: DeptParentOption): string {
      return `${'　'.repeat(option.depth)}${option.name}`
    },
    openCreate(parent?: unknown) {
      const row = parent as DeptTreeNode | undefined
      this.form = emptyForm(row?.id || 0)
      this.dialogVisible = true
    },
    openEdit(row: unknown) {
      this.form = presentForm(row as DeptRecord)
      this.dialogVisible = true
    },
    /** 普通业务失败在作用域里是静默的；写动作自己回显服务端文案（如删除保护）。 */
    reportFailure(error: unknown, fallback: string) {
      if (!(error instanceof PermissionRequestFailure)) {
        effects.notify(failureMessage(error) || fallback, 'error')
      }
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
        const saved = await this.list.runWrite(async () => {
          try {
            return await saveOrUpdate(this.form as DeptForm)
          } catch (error) {
            this.reportFailure(error, '保存失败')
            throw error
          }
        }, '操作成功')
        if (saved) this.dialogVisible = false
      } finally {
        this.saving = false
      }
    },
    remove(id: EntityId) {
      return this.list.confirmRemove(
        '此操作将永久删除该部门, 是否继续?',
        async () => {
          try {
            return await removeById(id)
          } catch (error) {
            this.reportFailure(error, '删除失败')
            throw error
          }
        },
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
</style>
