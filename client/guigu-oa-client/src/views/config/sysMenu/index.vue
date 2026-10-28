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
      <el-table-column prop="path" label="路由地址" />
      <el-table-column prop="component" label="组件路径" />
      <el-table-column prop="sortValue" label="排序" width="80" />
      <el-table-column label="操作" width="220" align="center">
        <template #default="scope">
          <el-button v-if="scope.row.type === 0" link @click="openCreate(scope.row)"
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form label-width="90px">
        <el-form-item v-if="form.parentName" label="上级菜单">
          <el-input :value="form.parentName" disabled />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.type" :disabled="typeLocked">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="如 el-icon-s-tools" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortValue" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="路由地址">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item v-if="form.type !== 0" label="组件路径">
          <el-input v-model="form.component" />
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
import { emptyForm, formForCreate, presentConfigMenuTree, presentForm } from '@/config/sys-menu'
import { listPageEffects } from '@/list-page-browser'
import type { EntityId, MenuNode } from '@/types/domain'

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
      return this.form.parentId ? '添加下级节点' : '添加目录节点'
    },
  },
  created() {
    this.fetchTree()
  },
  methods: {
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
