<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">菜单管理</h2>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate()">新建</el-button>
    </div>

    <ListLoadError :status="treeStatus" :error="treeError" @retry="fetchTree" />

    <el-table
      v-loading="treeStatus === 'loading'"
      :data="menuTree"
      :empty-text="treeStatus === 'ready' ? '暂无数据' : ''"
      row-key="id"
      border
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="name" label="菜单名称" />
      <el-table-column label="类型" width="80">
        <template #default="scope">{{ typeLabel(scope.row.type) }}</template>
      </el-table-column>
      <el-table-column prop="path" label="路由地址" />
      <el-table-column prop="component" label="组件路径" />
      <el-table-column prop="sortValue" label="排序" width="80" />
      <el-table-column label="操作" width="220" align="center">
        <template #default="scope">
          <el-button
            v-if="scope.row.type === 0 || scope.row.type === 1"
            link
            @click="openCreate(scope.row)"
            >添加下级</el-button
          >
          <el-button link @click="openEdit(scope.row)">编辑</el-button>
          <el-button
            link
            :disabled="scope.row.children && scope.row.children.length > 0"
            @click="remove(scope.row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="dialogRules" label-width="90px">
        <el-form-item v-if="form.parentName" label="上级菜单">
          <el-input :value="form.parentName" disabled />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.type" :disabled="typeLocked">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="如 el-icon-s-tools" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortValue" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="form.type !== 2" label="路由地址">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item v-if="form.type !== 2" label="组件路径">
          <el-input v-model="form.component" />
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 system:user:add" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
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
import { defineComponent } from 'vue'

import { findNodes, removeById, saveOrUpdate } from '@/api/sysMenu'
import ListLoadError from '@/components/ListLoadError.vue'
import { requiredRule, type RequiredRule } from '@/config/form-rules'
import { emptyForm, formForCreate, presentConfigMenuTree, presentForm } from '@/config/sys-menu'
import { listPageEffects } from '@/list/list-page-browser'
import type { EntityId, MenuNode } from '@/types/domain'
import type { FormInstance } from 'element-plus'

const effects = listPageEffects()

export default defineComponent({
  name: 'SysMenuList',
  components: { ListLoadError },
  data() {
    return {
      menuTree: [] as MenuNode[],
      treeStatus: 'loading' as 'loading' | 'ready' | 'failed',
      treeError: '',
      dialogVisible: false,
      saving: false,
      typeLocked: false,
      form: emptyForm(),
    }
  },
  computed: {
    dialogTitle() {
      if (this.form.id) return '编辑菜单'
      if (this.form.type === 2) return '添加按钮'
      return this.form.parentId ? '添加下级节点' : '添加目录节点'
    },
    dialogRules(): Record<string, RequiredRule[]> {
      const rules: Record<string, RequiredRule[]> = {
        name: [requiredRule('菜单名称不能为空')],
      }
      if (this.form.type === 2) {
        rules.perms = [requiredRule('按钮权限标识不能为空')]
      }
      return rules
    },
  },
  created() {
    this.fetchTree()
  },
  methods: {
    typeLabel(type: number | undefined) {
      return type === 0 ? '目录' : type === 1 ? '菜单' : type === 2 ? '按钮' : ''
    },
    fetchTree() {
      this.treeStatus = 'loading'
      this.treeError = ''
      findNodes()
        .then((response) => {
          this.menuTree = presentConfigMenuTree(response.data || [])
          this.treeStatus = 'ready'
        })
        .catch((error: unknown) => {
          this.treeStatus = 'failed'
          this.treeError = error instanceof Error ? error.message : '加载失败'
        })
    },
    openCreate(parent?: MenuNode) {
      this.form = formForCreate(parent)
      this.typeLocked = !!parent
      this.dialogVisible = true
    },
    openEdit(row: MenuNode) {
      this.form = presentForm(row)
      this.typeLocked = true
      this.dialogVisible = true
    },
    async save() {
      const form = this.$refs.formRef as FormInstance | undefined
      if (form) {
        try {
          await form.validate()
        } catch {
          return
        }
      }
      this.saving = true
      try {
        const response = await saveOrUpdate(this.form)
        effects.notify(response.message || '操作成功', 'success')
        this.dialogVisible = false
        this.fetchTree()
      } catch {
        /* 请求层已提示 */
      } finally {
        this.saving = false
      }
    },
    async remove(id: EntityId) {
      if (!(await effects.confirm('此操作将永久删除该菜单, 是否继续?'))) return
      try {
        const response = await removeById(id)
        effects.notify(response.message || '删除成功', 'success')
        this.fetchTree()
      } catch {
        /* 请求层已提示 */
      }
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
