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

<script>
import { doAssign, getByIdForAssign } from '@/api/sysMenu'
import { collectCheckedIds, mergeAssignMenuIds, presentAssignableConfigMenus } from '@/config/sys-menu'
import { useUserStore } from '@/store/modules/user'

export default {
  name: 'AssignAuth',
  data() {
    return {
      saving: false,
      fullTree: [],
      menuTree: [],
      treeProps: {
        children: 'children',
        label: 'name'
      }
    }
  },
  computed: {
    roleName() {
      return this.$route.query.roleName || ''
    }
  },
  created() {
    this.fetchTree()
  },
  methods: {
    fetchTree() {
      getByIdForAssign(this.$route.query.id).then(response => {
        this.fullTree = response.data || []
        this.menuTree = presentAssignableConfigMenus(this.fullTree)
        this.$nextTick(() => {
          this.$refs.tree.setCheckedKeys(collectCheckedIds(this.menuTree))
        })
      })
    },
    save() {
      const checkedNodes = this.$refs.tree.getCheckedNodes(false, true)
      const checkedIds = checkedNodes.map(node => node.id)
      const menuIdList = mergeAssignMenuIds(checkedIds, this.fullTree)
      this.saving = true
      doAssign(this.$route.query.id, menuIdList).then(response => {
        this.$message.success(response.message || '保存成功')
        return useUserStore().getInfo().catch(() => {})
      }).then(() => {
        this.$router.push('/system/sysRole')
      }).finally(() => {
        this.saving = false
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
