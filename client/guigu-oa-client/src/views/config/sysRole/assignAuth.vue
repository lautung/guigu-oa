<template>
  <div class="config-page">
    <div class="page-header">
      <h2 class="page-title">菜单授权</h2>
      <span class="role-name">角色：{{ roleName }}</span>
    </div>

    <el-tree
      ref="tree"
      :data="menuTree"
      node-key="id"
      show-checkbox
      default-expand-all
      :props="treeProps"
    />

    <div class="actions">
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      <el-button @click="$router.push('/system/sysRole')">返回</el-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'

import { doAssign, getByIdForAssign } from '@/api/sysMenu'
import {
  collectCheckedIds,
  mergeAssignMenuIds,
  presentAssignableConfigMenus,
} from '@/config/sys-menu'
import { useUserStore } from '@/store/modules/user'
import type { EntityId, MenuNode } from '@/types/domain'

interface MenuTreeInstance {
  setCheckedKeys(keys: EntityId[]): void
  getCheckedNodes(leafOnly?: boolean, includeHalfChecked?: boolean): MenuNode[]
}

function queryValue(value: string | null | Array<string | null> | undefined): string {
  return Array.isArray(value) ? value[0] || '' : value || ''
}

export default defineComponent({
  name: 'AssignAuth',
  data() {
    return {
      saving: false,
      fullTree: [] as MenuNode[],
      menuTree: [] as MenuNode[],
      treeProps: {
        children: 'children',
        label: 'name',
      },
    }
  },
  computed: {
    roleName() {
      return queryValue(this.$route.query.roleName)
    },
  },
  created() {
    this.fetchTree()
  },
  methods: {
    fetchTree() {
      const roleId = queryValue(this.$route.query.id)
      if (!roleId) return
      getByIdForAssign(roleId).then((response) => {
        this.fullTree = response.data || []
        this.menuTree = presentAssignableConfigMenus(this.fullTree)
        this.$nextTick(() => {
          const tree = this.$refs.tree as unknown as MenuTreeInstance | undefined
          tree?.setCheckedKeys(collectCheckedIds(this.menuTree))
        })
      })
    },
    save() {
      const roleId = queryValue(this.$route.query.id)
      const tree = this.$refs.tree as unknown as MenuTreeInstance | undefined
      if (!roleId || !tree) return
      const checkedNodes = tree.getCheckedNodes(false, true)
      const checkedIds = checkedNodes.flatMap((node) => (node.id == null ? [] : [node.id]))
      const menuIdList = mergeAssignMenuIds(checkedIds)
      this.saving = true
      doAssign(roleId, menuIdList)
        .then((response) => {
          this.$message.success(response.message || '保存成功')
          return useUserStore().recognition?.resume('/system/sysRole')
        })
        .catch(() => {})
        .finally(() => {
          this.saving = false
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
  align-items: baseline;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.role-name {
  color: #606266;
}

.actions {
  margin-top: 20px;
}
</style>
